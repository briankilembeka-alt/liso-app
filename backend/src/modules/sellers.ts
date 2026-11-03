import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Ctx } from "../context.js";
import { requireUser } from "./auth.js";
import { AppError, notFound } from "../lib/errors.js";
import { haversineKm, rankSellers } from "./ranking.js";

const GRACE_DAYS = 30;

interface SellerRow {
  id: string;
  business_name: string;
  kind: string;
  district: string;
  lat: number | null;
  lng: number | null;
  trust_score: string;
  call_requests_30d: number;
  suspended_until: Date | null;
  categories: string[];
}

const SELLER_SELECT = `
  SELECT s.id, s.business_name, s.kind, s.district, s.lat, s.lng, s.trust_score, s.call_requests_30d,
         s.suspended_until, COALESCE(array_agg(sc.category_id) FILTER (WHERE sc.category_id IS NOT NULL), '{}') AS categories
    FROM sellers s LEFT JOIN seller_categories sc ON sc.seller_id = s.id`;

function present(s: SellerRow, now: Date, extra: Record<string, unknown> = {}) {
  return {
    id: s.id,
    businessName: s.business_name,
    kind: s.kind,
    district: s.district,
    trustScore: Number(s.trust_score),
    categories: s.categories,
    canTakeCalls: !s.suspended_until || s.suspended_until <= now,
    ...extra,
  };
}

/** Returns the seller id if userId owns it, else throws 403. */
export async function requireSellerOwner(ctx: Ctx, sellerId: string, userId: string): Promise<void> {
  const r = await ctx.db.query("SELECT 1 FROM sellers WHERE id = $1 AND owner_user_id = $2", [sellerId, userId]);
  if (!r.rowCount) throw new AppError(403, "NOT_YOUR_SHOP", "only the seller can do this");
}

export function sellerRoutes(app: FastifyInstance, ctx: Ctx) {
  app.post("/sellers", async (req, reply) => {
    const userId = requireUser(req);
    const body = z
      .object({
        businessName: z.string().trim().min(2).max(80),
        kind: z.enum(["individual", "business"]),
        district: z.string().trim().min(2),
        lat: z.number().min(-90).max(90).optional(),
        lng: z.number().min(-180).max(180).optional(),
        categories: z.array(z.string()).min(1).max(5),
        ursbNumber: z.string().optional(),
        tin: z.string().optional(),
      })
      .parse(req.body);
    const now = ctx.now();
    // Registered businesses may join without URSB/TIN but must supply them within 30 days.
    const needsDocs = body.kind === "business" && !(body.ursbNumber && body.tin);
    const client = await ctx.db.connect();
    try {
      await client.query("BEGIN");
      const s = await client.query<{ id: string }>(
        `INSERT INTO sellers (owner_user_id, business_name, kind, district, lat, lng, ursb_number, tin, documents_due_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,
        [
          userId,
          body.businessName,
          body.kind,
          body.district,
          body.lat ?? null,
          body.lng ?? null,
          body.ursbNumber ?? null,
          body.tin ?? null,
          needsDocs ? new Date(now.getTime() + GRACE_DAYS * 86400_000) : null,
        ],
      );
      const id = s.rows[0]!.id;
      for (const c of body.categories) {
        await client.query("INSERT INTO seller_categories (seller_id, category_id) VALUES ($1, $2)", [id, c]);
      }
      await client.query("COMMIT");
      reply.code(201);
      return { id, documentsDueAt: needsDocs ? new Date(now.getTime() + GRACE_DAYS * 86400_000) : null };
    } catch (err) {
      await client.query("ROLLBACK");
      if ((err as { code?: string }).code === "23503") throw new AppError(400, "BAD_CATEGORY", "unknown category");
      throw err;
    } finally {
      client.release();
    }
  });

  /** Sellers for a category, ranked on merit. */
  app.get("/sellers", async (req) => {
    const q = z
      .object({
        category: z.string(),
        lat: z.coerce.number().optional(),
        lng: z.coerce.number().optional(),
        limit: z.coerce.number().int().min(1).max(50).default(20),
      })
      .parse(req.query);
    const now = ctx.now();
    const rows = (
      await ctx.db.query<SellerRow>(
        `${SELLER_SELECT}
          WHERE s.id IN (SELECT seller_id FROM seller_categories WHERE category_id = $1
                         OR category_id IN (SELECT id FROM categories WHERE parent_id = $1))
          GROUP BY s.id`,
        [q.category],
      )
    ).rows;
    const flag = await ctx.db.query<{ enabled: boolean }>("SELECT enabled FROM feature_flags WHERE key = 'sponsored_slots'");
    const sponsoredEnabled = flag.rows[0]?.enabled ?? false;
    const sponsoredIds = sponsoredEnabled
      ? new Set(
          (
            await ctx.db.query<{ seller_id: string }>(
              "SELECT seller_id FROM sponsored_slots WHERE category_id = $1 AND starts_at <= $2 AND ends_at > $2",
              [q.category, now],
            )
          ).rows.map((r) => r.seller_id),
        )
      : undefined;
    const here = q.lat !== undefined && q.lng !== undefined ? { lat: q.lat, lng: q.lng } : null;
    const ranked = rankSellers(
      rows.map((s) => ({
        ...s,
        matchesCategory: s.categories.includes(q.category),
        distanceKm: here && s.lat !== null && s.lng !== null ? haversineKm(here, { lat: s.lat, lng: s.lng }) : null,
        trustScore: Number(s.trust_score),
        callRequests30d: s.call_requests_30d,
      })),
      { sponsoredEnabled, sponsoredIds },
    );
    return {
      sellers: ranked.slice(0, q.limit).map((r) =>
        present(r.item, now, {
          sponsored: r.sponsored,
          distanceKm: r.item.distanceKm === null ? null : Math.round(r.item.distanceKm * 10) / 10,
        }),
      ),
    };
  });

  /** "Top sellers" strip on Home: ranked by video-call requests. */
  app.get("/sellers/top", async (req) => {
    const q = z.object({ category: z.string().optional(), limit: z.coerce.number().int().min(1).max(10).default(3) }).parse(req.query);
    const now = ctx.now();
    const rows = (
      await ctx.db.query<SellerRow>(
        `${SELLER_SELECT}
          ${q.category ? "WHERE s.id IN (SELECT seller_id FROM seller_categories WHERE category_id = $2)" : ""}
          GROUP BY s.id ORDER BY s.call_requests_30d DESC, s.trust_score DESC LIMIT $1`,
        q.category ? [q.limit, q.category] : [q.limit],
      )
    ).rows;
    return { sellers: rows.map((s) => present(s, now)) };
  });

  app.get("/sellers/:id", async (req) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const r = await ctx.db.query<SellerRow>(`${SELLER_SELECT} WHERE s.id = $1 GROUP BY s.id`, [id]);
    if (!r.rows[0]) throw notFound("seller");
    const deals = await ctx.db.query<{ n: string }>(
      "SELECT count(*) AS n FROM deals WHERE seller_id = $1 AND status = 'completed'",
      [id],
    );
    return present(r.rows[0], ctx.now(), { completedDeals: Number(deals.rows[0]!.n) });
  });
}
