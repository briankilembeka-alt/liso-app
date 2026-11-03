-- Liso milestone 1 schema: accounts, catalog, sellers, calls, deals.
-- Money is stored in whole Uganda shillings (UGX has no minor unit in practice).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  phone         text NOT NULL UNIQUE,              -- E.164, e.g. +256772123456
  display_name  text,
  language      text NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'lg')),
  -- Buyers verify ID the first time they tap Call; browsing needs none.
  id_verified_at timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE otp_codes (
  phone       text NOT NULL,
  code_hash   text NOT NULL,
  expires_at  timestamptz NOT NULL,
  attempts    int NOT NULL DEFAULT 0,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX otp_codes_phone ON otp_codes (phone, created_at DESC);

CREATE TABLE sessions (
  token_hash  text PRIMARY KEY,
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at  timestamptz NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- Category tree, at most 3 levels (e.g. Clothes > Men > Trousers).
CREATE TABLE categories (
  id         text PRIMARY KEY,                       -- slug, e.g. "phones"
  parent_id  text REFERENCES categories(id),
  depth      int NOT NULL CHECK (depth BETWEEN 1 AND 3),
  name_en    text NOT NULL,
  name_lg    text NOT NULL,
  rank       int NOT NULL DEFAULT 100                -- lower shows first
);

CREATE TABLE sellers (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id    uuid NOT NULL REFERENCES users(id),
  business_name    text NOT NULL,
  kind             text NOT NULL CHECK (kind IN ('individual', 'business')),
  district         text NOT NULL,
  lat              double precision,
  lng              double precision,
  -- Verification. Individuals: National ID + matching MoMo name.
  -- Businesses may start without URSB/TIN, with a 30-day grace period.
  national_id_verified boolean NOT NULL DEFAULT false,
  momo_name_matches    boolean NOT NULL DEFAULT false,
  ursb_number      text,
  tin              text,
  documents_due_at timestamptz,
  trust_score      numeric(4,3) NOT NULL DEFAULT 0.5 CHECK (trust_score BETWEEN 0 AND 1),
  call_requests_30d int NOT NULL DEFAULT 0,          -- popularity, drives "Top sellers"
  suspended_until  timestamptz,                      -- blocks new calls/lives only
  created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE seller_categories (
  seller_id   uuid NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
  category_id text NOT NULL REFERENCES categories(id),
  PRIMARY KEY (seller_id, category_id)
);

-- Paid placement is built in but switched off for the pilot (see feature_flags).
CREATE TABLE sponsored_slots (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  seller_id   uuid NOT NULL REFERENCES sellers(id),
  category_id text NOT NULL REFERENCES categories(id),
  starts_at   timestamptz NOT NULL,
  ends_at     timestamptz NOT NULL
);

CREATE TABLE feature_flags (
  key     text PRIMARY KEY,
  enabled boolean NOT NULL
);
INSERT INTO feature_flags (key, enabled) VALUES ('sponsored_slots', false);

CREATE TABLE calls (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id     uuid NOT NULL REFERENCES users(id),
  seller_id    uuid NOT NULL REFERENCES sellers(id),
  status       text NOT NULL CHECK (status IN ('requested', 'active', 'declined', 'ended', 'missed')),
  room_ref     text,                                 -- provider's room/stage id
  recording_ref text,                                -- kept only if the call ends in a deal
  recording_delete_after timestamptz,
  requested_at timestamptz NOT NULL DEFAULT now(),
  started_at   timestamptz,
  ended_at     timestamptz
);
CREATE INDEX calls_seller_status ON calls (seller_id, status);

CREATE TABLE deals (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  call_id      uuid REFERENCES calls(id),
  buyer_id     uuid NOT NULL REFERENCES users(id),
  seller_id    uuid NOT NULL REFERENCES sellers(id),
  item         text NOT NULL,
  price_ugx    bigint NOT NULL CHECK (price_ugx > 0),
  fee_ugx      bigint NOT NULL CHECK (fee_ugx >= 0),  -- paid by the seller
  delivery     text NOT NULL CHECK (delivery IN ('seller', 'safe_boda', 'partner', 'pickup')),
  status       text NOT NULL,
  handover_code text NOT NULL,                        -- 4 digits, shown to the buyer
  release_after timestamptz,                         -- 48 h after delivery, if no complaint
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- Append-only, hash-chained record of everything that happens in a deal.
-- Each row's hash covers the previous row's hash, so editing history breaks the chain.
CREATE TABLE deal_events (
  deal_id    uuid NOT NULL REFERENCES deals(id),
  seq        int NOT NULL,
  type       text NOT NULL,
  actor_id   uuid,
  payload    jsonb NOT NULL DEFAULT '{}',
  at         timestamptz NOT NULL DEFAULT now(),
  prev_hash  text NOT NULL,
  hash       text NOT NULL,
  PRIMARY KEY (deal_id, seq)
);

CREATE OR REPLACE FUNCTION deal_events_no_change() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'deal_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER deal_events_append_only
  BEFORE UPDATE OR DELETE ON deal_events
  FOR EACH ROW EXECUTE FUNCTION deal_events_no_change();
