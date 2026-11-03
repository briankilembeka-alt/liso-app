import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Ctx } from "../context.js";

export function catalogRoutes(app: FastifyInstance, ctx: Ctx) {
  /** The whole tree is small (~20 KB at full size), so the phone caches it. */
  app.get("/categories", async (req, reply) => {
    const { lang } = z.object({ lang: z.enum(["en", "lg"]).default("en") }).parse(req.query);
    const r = await ctx.db.query(
      `SELECT id, parent_id AS "parentId", depth, ${lang === "lg" ? "name_lg" : "name_en"} AS name, rank
         FROM categories ORDER BY depth, rank, id`,
    );
    reply.header("cache-control", "public, max-age=3600");
    return { categories: r.rows };
  });
}
