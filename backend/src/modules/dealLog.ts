import type pg from "pg";
import { canonicalJson, sha256 } from "../lib/crypto.js";

export const GENESIS = "0".repeat(64);

export interface DealEvent {
  dealId: string;
  seq: number;
  type: string;
  actorId: string | null;
  payload: Record<string, unknown>;
  at: string; // ISO time
  prevHash: string;
  hash: string;
}

export function hashEvent(e: Omit<DealEvent, "hash"> & { hash?: string }): string {
  const { prevHash, hash: _ignored, ...body } = e;
  return sha256(prevHash + canonicalJson(body));
}

/** Append an event to a deal's chain. Call inside a transaction that holds the deal row lock. */
export async function appendEvent(
  c: pg.PoolClient,
  dealId: string,
  type: string,
  actorId: string | null,
  payload: Record<string, unknown>,
  at: Date,
): Promise<DealEvent> {
  const last = await c.query<{ seq: number; hash: string }>(
    "SELECT seq, hash FROM deal_events WHERE deal_id = $1 ORDER BY seq DESC LIMIT 1",
    [dealId],
  );
  const seq = (last.rows[0]?.seq ?? 0) + 1;
  const base = { dealId, seq, type, actorId, payload, at: at.toISOString(), prevHash: last.rows[0]?.hash ?? GENESIS };
  const hash = hashEvent(base);
  await c.query(
    `INSERT INTO deal_events (deal_id, seq, type, actor_id, payload, at, prev_hash, hash)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [dealId, seq, type, actorId, payload, at, base.prevHash, hash],
  );
  return { ...base, hash };
}

export async function readEvents(db: pg.Pool | pg.PoolClient, dealId: string): Promise<DealEvent[]> {
  const r = await db.query<{
    seq: number; type: string; actor_id: string | null; payload: Record<string, unknown>; at: Date; prev_hash: string; hash: string;
  }>("SELECT seq, type, actor_id, payload, at, prev_hash, hash FROM deal_events WHERE deal_id = $1 ORDER BY seq", [dealId]);
  return r.rows.map((x) => ({
    dealId, seq: x.seq, type: x.type, actorId: x.actor_id, payload: x.payload, at: x.at.toISOString(), prevHash: x.prev_hash, hash: x.hash,
  }));
}

/** Recompute the chain. Returns the first broken seq, or null when the record is intact. */
export function verifyChain(events: DealEvent[]): number | null {
  let prev = GENESIS;
  for (const [i, e] of events.entries()) {
    if (e.seq !== i + 1 || e.prevHash !== prev || hashEvent(e) !== e.hash) return e.seq;
    prev = e.hash;
  }
  return null;
}
