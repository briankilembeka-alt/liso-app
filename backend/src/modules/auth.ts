import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import type { Ctx } from "../context.js";
import { AppError } from "../lib/errors.js";
import { digits, newToken, safeEqual, sha256 } from "../lib/crypto.js";
import { normaliseUgPhone } from "../lib/phone.js";

const OTP_MINUTES = 10;
const OTP_MAX_ATTEMPTS = 5;
const OTP_PER_HOUR = 5;
const SESSION_DAYS = 90;

const phoneSchema = z.string().transform((s, ctx) => {
  const p = normaliseUgPhone(s);
  if (!p) ctx.addIssue({ code: "custom", message: "not a Ugandan mobile number" });
  return p ?? "";
});

declare module "fastify" {
  interface FastifyRequest {
    userId?: string;
  }
}

/** Returns the signed-in user's id, or throws 401. */
export function requireUser(req: FastifyRequest): string {
  if (!req.userId) throw new AppError(401, "UNAUTHENTICATED", "sign in first");
  return req.userId;
}

export function authRoutes(app: FastifyInstance, ctx: Ctx) {
  // Resolve "Authorization: Bearer <token>" to req.userId on every request.
  app.addHook("onRequest", async (req) => {
    const h = req.headers.authorization;
    if (!h?.startsWith("Bearer ")) return;
    const r = await ctx.db.query<{ user_id: string }>(
      "SELECT user_id FROM sessions WHERE token_hash = $1 AND expires_at > $2",
      [sha256(h.slice(7)), ctx.now()],
    );
    req.userId = r.rows[0]?.user_id;
  });

  app.post("/auth/otp", async (req) => {
    const { phone } = z.object({ phone: phoneSchema }).parse(req.body);
    const now = ctx.now();
    const recent = await ctx.db.query<{ n: string }>(
      "SELECT count(*) AS n FROM otp_codes WHERE phone = $1 AND created_at > $2",
      [phone, new Date(now.getTime() - 3600_000)],
    );
    if (Number(recent.rows[0]?.n) >= OTP_PER_HOUR) {
      throw new AppError(429, "TOO_MANY_CODES", "too many codes requested, try again later");
    }
    const code = digits(6);
    await ctx.db.query(
      "INSERT INTO otp_codes (phone, code_hash, expires_at, created_at) VALUES ($1, $2, $3, $4)",
      [phone, sha256(`${phone}:${code}`), new Date(now.getTime() + OTP_MINUTES * 60_000), now],
    );
    await ctx.sms.send(phone, `Your Liso code is ${code}. It expires in ${OTP_MINUTES} minutes.`);
    return { sent: true, phone };
  });

  app.post("/auth/verify", async (req) => {
    const body = z.object({ phone: phoneSchema, code: z.string().regex(/^\d{6}$/) }).parse(req.body);
    const now = ctx.now();
    const otp = await ctx.db.query<{ code_hash: string; attempts: number; created_at: Date }>(
      `SELECT code_hash, attempts, created_at FROM otp_codes
        WHERE phone = $1 AND expires_at > $2 ORDER BY created_at DESC LIMIT 1`,
      [body.phone, now],
    );
    const row = otp.rows[0];
    const bad = new AppError(400, "BAD_CODE", "that code is wrong or has expired");
    if (!row || row.attempts >= OTP_MAX_ATTEMPTS) throw bad;
    if (!safeEqual(row.code_hash, sha256(`${body.phone}:${body.code}`))) {
      await ctx.db.query(
        "UPDATE otp_codes SET attempts = attempts + 1 WHERE phone = $1 AND created_at = $2",
        [body.phone, row.created_at],
      );
      throw bad;
    }
    await ctx.db.query("DELETE FROM otp_codes WHERE phone = $1", [body.phone]);
    const user = await ctx.db.query<{ id: string; created: boolean }>(
      `INSERT INTO users (phone) VALUES ($1)
       ON CONFLICT (phone) DO UPDATE SET phone = EXCLUDED.phone
       RETURNING id, (xmax = 0) AS created`,
      [body.phone],
    );
    const u = user.rows[0]!;
    const token = newToken();
    await ctx.db.query("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES ($1, $2, $3)", [
      sha256(token),
      u.id,
      new Date(now.getTime() + SESSION_DAYS * 86400_000),
    ]);
    return { token, userId: u.id, isNewUser: u.created };
  });

  app.get("/me", async (req) => {
    const id = requireUser(req);
    const r = await ctx.db.query(
      "SELECT id, phone, display_name, language, id_verified_at FROM users WHERE id = $1",
      [id],
    );
    return r.rows[0];
  });

  app.patch("/me", async (req) => {
    const id = requireUser(req);
    const body = z
      .object({ displayName: z.string().trim().min(1).max(60).optional(), language: z.enum(["en", "lg"]).optional() })
      .parse(req.body);
    const r = await ctx.db.query(
      `UPDATE users SET display_name = COALESCE($2, display_name), language = COALESCE($3, language)
        WHERE id = $1 RETURNING id, phone, display_name, language, id_verified_at`,
      [id, body.displayName ?? null, body.language ?? null],
    );
    return r.rows[0];
  });

  /**
   * Buyer ID check, asked the first time they tap Call.
   * Milestone 1 records the National ID number only; a real check (NIRA via a KYC partner)
   * replaces this before the pilot goes live.
   */
  app.post("/me/id-verification", async (req) => {
    const id = requireUser(req);
    z.object({ nationalIdNumber: z.string().regex(/^[A-Z0-9]{14}$/, "a NIN has 14 letters and digits") }).parse(
      req.body,
    );
    const r = await ctx.db.query<{ id_verified_at: Date }>(
      "UPDATE users SET id_verified_at = COALESCE(id_verified_at, $2) WHERE id = $1 RETURNING id_verified_at",
      [id, ctx.now()],
    );
    return { verified: true, verifiedAt: r.rows[0]!.id_verified_at };
  });
}
