# Architecture

**Rent the video, build the trust.** Video is rented from Amazon IVS. What Liso builds is the trust layer: verification, recorded calls, the deal record and held mobile money.

## Pieces

- **Android app** (Kotlin, Compose). Under 20 MB per phone: one APK per CPU type, code shrinking, no bundled fonts. 360p video by default.
- **API** (`backend/`). One Fastify service in modules: `auth`, `catalog`, `sellers`, `calls`, `deals`. Postgres is the only store in milestone 1.
- **Video** (`backend/src/providers/video.ts`). 1:1 calls are WebRTC rooms on IVS Real-Time ("stages"). Live shows (milestone 3) use IVS low-latency streaming. The `VideoProvider` interface keeps IVS swappable for LiveKit.
- **Payments** (`backend/src/providers/payments.ts`). One `WalletProvider` interface, MTN MoMo first and Airtel Money second, both in sandbox until the Bank of Uganda licensing question is settled.
- **SMS** for sign-in codes. Prints to the log in development; a Ugandan SMS gateway goes in before the pilot.

## Trust rules built into the code

| Rule | Where |
| --- | --- |
| Buyers verify National ID the first time they tap Call; browsing is free | `POST /calls` returns `ID_VERIFICATION_REQUIRED` |
| Businesses can join without URSB/TIN and get 30 days to supply them | `sellers.documents_due_at` |
| Suspension blocks new calls only | `sellers.suspended_until`, checked in `POST /calls` |
| Ranking is on merit; "Sponsored" slots are built but off | `modules/ranking.ts`, flag `sponsored_slots` |
| Calls are recorded, both sides told; recordings of calls without a deal are deleted within a day | `calls.recording_delete_after` |
| The deal record can't be edited | `deal_events` is hash-chained and append-only (database trigger) |
| Handover is proved with a 4-digit code only the buyer sees | `POST /deals/:id/delivered` |
| No complaint within 48 h of delivery and the deal completes on its own | `autoCompleteDue()` |
| Liso's fee (3%) is paid by the seller | `feeFor()` in `modules/deals.ts` |

## Deal states

```
agreed -> accepted -> delivered -> completed
agreed -> cancelled
```

Milestone 2 inserts `paid` (money held) between `accepted` and `delivered`. Milestone 3 adds disputes.
