import Fastify, { type FastifyInstance } from "fastify";
import { ZodError } from "zod";
import type { Ctx } from "./context.js";
import { AppError } from "./lib/errors.js";
import { authRoutes } from "./modules/auth.js";
import { catalogRoutes } from "./modules/catalog.js";
import { sellerRoutes } from "./modules/sellers.js";
import { callRoutes } from "./modules/calls.js";
import { dealRoutes } from "./modules/deals.js";

export function buildApp(ctx: Ctx, opts: { logger?: boolean } = {}): FastifyInstance {
  const app = Fastify({ logger: opts.logger ?? false });

  app.setErrorHandler((err, _req, reply) => {
    if (err instanceof AppError) {
      return reply.code(err.status).send({ error: { code: err.code, message: err.message } });
    }
    if (err instanceof ZodError) {
      return reply.code(400).send({ error: { code: "BAD_REQUEST", message: "invalid request", issues: err.issues } });
    }
    const status = (err as { statusCode?: number }).statusCode;
    if (status && status < 500) {
      return reply.code(status).send({ error: { code: "BAD_REQUEST", message: (err as Error).message } });
    }
    app.log.error(err);
    return reply.code(500).send({ error: { code: "INTERNAL", message: "something went wrong" } });
  });

  app.get("/health", async () => ({ ok: true }));
  authRoutes(app, ctx);
  catalogRoutes(app, ctx);
  sellerRoutes(app, ctx);
  callRoutes(app, ctx);
  dealRoutes(app, ctx);
  return app;
}
