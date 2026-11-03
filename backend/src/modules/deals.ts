import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Ctx } from "../context.js";
import { tx } from "../db.js";
import { requireUser } from "./auth.js";
import { AppError, conflict, forbidden, notFound } from "../lib/errors.js";
import { digits, safeEqual } from "../lib/crypto.js";
import { appendEvent, readEvents, verifyChain } from "./dealLog.js";

/** Liso's fee, paid by the seller. 3% is the business-plan assumption for the pilot. */
export const FEE_BASIS_POINTS = 300n;
/** Money auto-releases to the seller if the buyer raises no complaint within 48 h of delivery. */
export const COMPLAINT_WINDOW_MS = 48 * 3600_000;

export const feeFor = (priceUgx: bigint) => (priceUgx * FEE_BASIS_POINTS + 5_000n) / 10_000n;

/**
 * Deal life cycle in milestone 1 (payments slot in between accepted and delivered in milestone 2):
 *   agreed -> accepted -> delivered -> completed
 *   agreed -> cancelled
 */
type Status = "agreed" | "accepted" | "delivered" | "completed" | "cancelled";

interface DealRow {
  id: string;
  call_id: string | null;
  buyer_id: string;
  seller_id: string;
  owner_user_id: string;
  item: string;
  price_ugx: string;
  fee_ugx: string;
  delivery: string;
  status: Status;
  handover_code: string;
  release_after: Date | null;
  created_at: Date;
}

const idParam = z.object({ id: z.string().uuid() });

export function dealRoutes(app: FastifyInstance, ctx: Ctx) {
  /** Locks the deal row for the rest of the transaction. */
  async function lockDeal(c: import("pg").PoolClient, id: string): Promise<DealRow> {
    const r = await c.query<DealRow>(
      `SELECT d.*, s.owner_user_id FROM deals d JOIN sellers s ON s.id = d.seller_id WHERE d.id = $1 FOR UPDATE OF d`,
      [id],
    );
    if (!r.rows[0]) throw notFound("deal");
    return r.rows[0];
  }

  function present(d: DealRow, viewerId: string) {
    return {
      id: d.id,
      callId: d.call_id,
      item: d.item,
      priceUgx: d.price_ugx,
      feeUgx: d.fee_ugx,
      sellerReceivesUgx: (BigInt(d.price_ugx) - BigInt(d.fee_ugx)).toString(),
      delivery: d.delivery,
      status: d.status,
      releaseAfter: d.release_after,
      // Only the buyer sees the code; they give it to the seller or rider at handover.
      handoverCode: viewerId === d.buyer_id ? d.handover_code : undefined,
      createdAt: d.created_at,
    };
  }

  /** Seller writes up the deal agreed on the call. */
  app.post("/deals", async (req, reply) => {
    const userId = requireUser(req);
    const body = z
      .object({
        callId: z.string().uuid(),
        item: z.string().trim().min(2).max(200),
        priceUgx: z.coerce.bigint().refine((v) => v > 0n && v <= 1_000_000_000n, "price out of range"),
        delivery: z.enum(["seller", "safe_boda", "partner", "pickup"]).default("seller"),
      })
      .parse(req.body);
    const now = ctx.now();
    const deal = await tx(ctx.db, async (c) => {
      const call = await c.query<{ buyer_id: string; seller_id: string; owner_user_id: string; status: string }>(
        `SELECT c.buyer_id, c.seller_id, s.owner_user_id, c.status FROM calls c JOIN sellers s ON s.id = c.seller_id
          WHERE c.id = $1 FOR UPDATE OF c`,
        [body.callId],
      );
      const cl = call.rows[0];
      if (!cl) throw notFound("call");
      if (cl.owner_user_id !== userId) throw forbidden("only the seller can write up the deal");
      if (cl.status !== "active" && cl.status !== "ended") throw conflict("BAD_STATE", "deals come from a call that happened");
      const fee = feeFor(body.priceUgx);
      const d = await c.query<{ id: string }>(
        `INSERT INTO deals (call_id, buyer_id, seller_id, item, price_ugx, fee_ugx, delivery, status, handover_code, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,'agreed',$8,$9) RETURNING id`,
        [body.callId, cl.buyer_id, cl.seller_id, body.item, body.priceUgx.toString(), fee.toString(), body.delivery, digits(4), now],
      );
      const id = d.rows[0]!.id;
      // Keep the call recording: it is the evidence for this deal.
      await c.query("UPDATE calls SET recording_delete_after = NULL WHERE id = $1", [body.callId]);
      await appendEvent(c, id, "deal.agreed", userId, {
        item: body.item, priceUgx: body.priceUgx.toString(), feeUgx: fee.toString(), delivery: body.delivery, callId: body.callId,
      }, now);
      return lockDeal(c, id);
    });
    reply.code(201);
    return present(deal, userId);
  });

  app.get("/deals/:id", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    const r = await ctx.db.query<DealRow>(
      "SELECT d.*, s.owner_user_id FROM deals d JOIN sellers s ON s.id = d.seller_id WHERE d.id = $1",
      [id],
    );
    const d = r.rows[0];
    if (!d) throw notFound("deal");
    if (d.buyer_id !== userId && d.owner_user_id !== userId) throw forbidden();
    const events = await readEvents(ctx.db, id);
    const brokenAt = verifyChain(events);
    return { ...present(d, userId), record: { intact: brokenAt === null, brokenAt, events } };
  });

  /** Moves a deal from one status to the next, checking who may do it, and logs the event. */
  function transition(opts: {
    path: string;
    from: Status[];
    to: Status;
    by: "buyer" | "seller" | "either";
    event: string;
    body?: z.ZodTypeAny;
    check?: (d: DealRow, body: any) => void;
    extra?: (d: DealRow, now: Date) => { releaseAfter?: Date | null };
  }) {
    app.post(`/deals/:id/${opts.path}`, async (req) => {
      const userId = requireUser(req);
      const { id } = idParam.parse(req.params);
      const body = opts.body ? opts.body.parse(req.body ?? {}) : {};
      const now = ctx.now();
      const d = await tx(ctx.db, async (c) => {
        const d = await lockDeal(c, id);
        const role = userId === d.buyer_id ? "buyer" : userId === d.owner_user_id ? "seller" : null;
        if (!role || (opts.by !== "either" && opts.by !== role)) throw forbidden(`only the ${opts.by} can do this`);
        if (!opts.from.includes(d.status)) throw conflict("BAD_STATE", `deal is ${d.status}`);
        opts.check?.(d, body);
        const extra = opts.extra?.(d, now) ?? {};
        await c.query("UPDATE deals SET status = $2, release_after = COALESCE($3, release_after) WHERE id = $1", [
          id, opts.to, extra.releaseAfter ?? null,
        ]);
        await appendEvent(c, id, opts.event, userId, { from: d.status, to: opts.to }, now);
        if (opts.to === "completed" && d.call_id) {
          await c.query("UPDATE calls SET recording_delete_after = $2 WHERE id = $1", [d.call_id, now]);
        }
        return lockDeal(c, id);
      });
      return present(d, userId);
    });
  }

  transition({ path: "accept", from: ["agreed"], to: "accepted", by: "buyer", event: "deal.buyer_accepted" });
  transition({ path: "cancel", from: ["agreed"], to: "cancelled", by: "either", event: "deal.cancelled" });
  transition({
    path: "delivered",
    from: ["accepted"],
    to: "delivered",
    by: "seller",
    event: "deal.delivered",
    body: z.object({ handoverCode: z.string().regex(/^\d{4}$/) }),
    check: (d, body: { handoverCode: string }) => {
      if (!safeEqual(d.handover_code, body.handoverCode)) {
        throw new AppError(400, "BAD_HANDOVER_CODE", "ask the buyer for the 4-digit code on their phone");
      }
    },
    extra: (_d, now) => ({ releaseAfter: new Date(now.getTime() + COMPLAINT_WINDOW_MS) }),
  });
  transition({ path: "confirm", from: ["delivered"], to: "completed", by: "buyer", event: "deal.buyer_confirmed" });
}

/**
 * Completes delivered deals whose 48-hour complaint window has passed. Run on a schedule.
 * Once complaints exist (milestone 3), a deal with an open complaint is skipped.
 */
export async function autoCompleteDue(ctx: Ctx): Promise<string[]> {
  const now = ctx.now();
  const due = await ctx.db.query<{ id: string }>(
    "SELECT id FROM deals WHERE status = 'delivered' AND release_after <= $1",
    [now],
  );
  const done: string[] = [];
  for (const { id } of due.rows) {
    await tx(ctx.db, async (c) => {
      const r = await c.query<{ status: string; call_id: string | null }>(
        "SELECT status, call_id FROM deals WHERE id = $1 FOR UPDATE",
        [id],
      );
      if (r.rows[0]?.status !== "delivered") return;
      await c.query("UPDATE deals SET status = 'completed' WHERE id = $1", [id]);
      await appendEvent(c, id, "deal.auto_completed", null, { reason: "no complaint within 48 hours" }, now);
      if (r.rows[0].call_id) {
        await c.query("UPDATE calls SET recording_delete_after = $2 WHERE id = $1", [r.rows[0].call_id, now]);
      }
      done.push(id);
    });
  }
  return done;
}
