import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Ctx } from "../context.js";
import { requireUser } from "./auth.js";
import { requireSellerOwner } from "./sellers.js";
import { AppError, conflict, forbidden, notFound } from "../lib/errors.js";

/** Recordings of calls that don't end in a deal are deleted within a day. */
export const NO_DEAL_RECORDING_TTL_MS = 24 * 3600_000;

interface CallRow {
  id: string;
  buyer_id: string;
  seller_id: string;
  owner_user_id: string;
  status: string;
  room_ref: string | null;
}

async function loadCall(ctx: Ctx, id: string): Promise<CallRow> {
  const r = await ctx.db.query<CallRow>(
    `SELECT c.id, c.buyer_id, c.seller_id, s.owner_user_id, c.status, c.room_ref
       FROM calls c JOIN sellers s ON s.id = c.seller_id WHERE c.id = $1`,
    [id],
  );
  if (!r.rows[0]) throw notFound("call");
  return r.rows[0];
}

const idParam = z.object({ id: z.string().uuid() });

export function callRoutes(app: FastifyInstance, ctx: Ctx) {
  /** Buyer taps Call on a seller. */
  app.post("/calls", async (req, reply) => {
    const buyerId = requireUser(req);
    const { sellerId } = z.object({ sellerId: z.string().uuid() }).parse(req.body);
    const now = ctx.now();
    const u = await ctx.db.query<{ id_verified_at: Date | null }>("SELECT id_verified_at FROM users WHERE id = $1", [buyerId]);
    if (!u.rows[0]?.id_verified_at) {
      throw new AppError(403, "ID_VERIFICATION_REQUIRED", "verify your National ID before your first call");
    }
    const s = await ctx.db.query<{ owner_user_id: string; suspended_until: Date | null }>(
      "SELECT owner_user_id, suspended_until FROM sellers WHERE id = $1",
      [sellerId],
    );
    const seller = s.rows[0];
    if (!seller) throw notFound("seller");
    if (seller.owner_user_id === buyerId) throw new AppError(400, "OWN_SHOP", "you can't call your own shop");
    if (seller.suspended_until && seller.suspended_until > now) {
      throw conflict("SELLER_SUSPENDED", "this seller can't take calls right now");
    }
    const c = await ctx.db.query<{ id: string }>(
      "INSERT INTO calls (buyer_id, seller_id, status, requested_at) VALUES ($1, $2, 'requested', $3) RETURNING id",
      [buyerId, sellerId, now],
    );
    await ctx.db.query("UPDATE sellers SET call_requests_30d = call_requests_30d + 1 WHERE id = $1", [sellerId]);
    reply.code(201);
    return { id: c.rows[0]!.id, status: "requested", recorded: true };
  });

  /** Seller's incoming call requests. */
  app.get("/sellers/:id/calls/incoming", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    await requireSellerOwner(ctx, id, userId);
    const r = await ctx.db.query(
      `SELECT c.id, c.requested_at AS "requestedAt", u.display_name AS "buyerName"
         FROM calls c JOIN users u ON u.id = c.buyer_id
        WHERE c.seller_id = $1 AND c.status = 'requested' ORDER BY c.requested_at`,
      [id],
    );
    return { calls: r.rows };
  });

  /** Either side polls this to see when the seller has answered. */
  app.get("/calls/:id", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    const call = await loadCall(ctx, id);
    if (call.buyer_id !== userId && call.owner_user_id !== userId) throw forbidden();
    return { id: call.id, sellerId: call.seller_id, status: call.status, role: call.buyer_id === userId ? "buyer" : "seller" };
  });

  app.post("/calls/:id/accept", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    const call = await loadCall(ctx, id);
    if (call.owner_user_id !== userId) throw forbidden("only the seller can accept");
    if (call.status !== "requested") throw conflict("BAD_STATE", `call is ${call.status}`);
    const { roomRef } = await ctx.video.createRoom(id);
    const r = await ctx.db.query(
      "UPDATE calls SET status = 'active', room_ref = $2, started_at = $3 WHERE id = $1 AND status = 'requested'",
      [id, roomRef, ctx.now()],
    );
    if (!r.rowCount) {
      await ctx.video.closeRoom(roomRef);
      throw conflict("BAD_STATE", "call changed while accepting");
    }
    return { status: "active", join: await ctx.video.joinToken(roomRef, userId, "seller"), recorded: true };
  });

  app.post("/calls/:id/decline", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    const call = await loadCall(ctx, id);
    if (call.owner_user_id !== userId) throw forbidden("only the seller can decline");
    if (call.status !== "requested") throw conflict("BAD_STATE", `call is ${call.status}`);
    await ctx.db.query("UPDATE calls SET status = 'declined', ended_at = $2 WHERE id = $1", [id, ctx.now()]);
    return { status: "declined" };
  });

  /** Buyer fetches a token once the seller has accepted. */
  app.post("/calls/:id/join", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    const call = await loadCall(ctx, id);
    if (call.buyer_id !== userId) throw forbidden();
    if (call.status !== "active" || !call.room_ref) throw conflict("BAD_STATE", `call is ${call.status}`);
    return { join: await ctx.video.joinToken(call.room_ref, userId, "buyer"), recorded: true };
  });

  app.post("/calls/:id/end", async (req) => {
    const userId = requireUser(req);
    const { id } = idParam.parse(req.params);
    const call = await loadCall(ctx, id);
    if (call.buyer_id !== userId && call.owner_user_id !== userId) throw forbidden();
    if (call.status === "ended") return { status: "ended" };
    if (call.status !== "active") throw conflict("BAD_STATE", `call is ${call.status}`);
    const now = ctx.now();
    // A deal made on this call later clears recording_delete_after so the recording is kept.
    await ctx.db.query(
      "UPDATE calls SET status = 'ended', ended_at = $2, recording_delete_after = $3 WHERE id = $1",
      [id, now, new Date(now.getTime() + NO_DEAL_RECORDING_TTL_MS)],
    );
    if (call.room_ref) await ctx.video.closeRoom(call.room_ref);
    return { status: "ended" };
  });
}
