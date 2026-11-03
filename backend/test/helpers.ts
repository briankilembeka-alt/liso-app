import { createDb, type Db } from "../src/db.js";
import { migrate } from "../src/migrate.js";
import { buildApp } from "../src/app.js";
import { ConsoleSms } from "../src/providers/sms.js";
import { FakeVideo } from "../src/providers/video.js";
import type { Ctx } from "../src/context.js";

const url = process.env.TEST_DATABASE_URL ?? "postgres://liso:liso@localhost:5432/liso_test";

export interface TestEnv {
  ctx: Ctx & { sms: ConsoleSms; video: FakeVideo };
  app: ReturnType<typeof buildApp>;
  clock: { now: Date; advance(ms: number): void };
  close(): Promise<void>;
}

export async function setup(): Promise<TestEnv> {
  const db: Db = createDb(url);
  await db.query("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
  await migrate(db);
  const clock = {
    now: new Date("2026-11-03T10:00:00Z"),
    advance(ms: number) {
      this.now = new Date(this.now.getTime() + ms);
    },
  };
  const ctx = { db, sms: new ConsoleSms(), video: new FakeVideo(), now: () => clock.now };
  const app = buildApp(ctx);
  await app.ready();
  return { ctx, app, clock, close: async () => { await app.close(); await db.end(); } };
}

/** Sign in by phone, reading the OTP from the fake SMS outbox. Returns an auth header. */
export async function signIn(env: TestEnv, phone: string) {
  await env.app.inject({ method: "POST", url: "/auth/otp", payload: { phone } });
  const code = /\b(\d{6})\b/.exec(env.ctx.sms.sent.at(-1)!.text)![1];
  const res = await env.app.inject({ method: "POST", url: "/auth/verify", payload: { phone, code } });
  const body = res.json();
  return { userId: body.userId as string, headers: { authorization: `Bearer ${body.token}` } };
}
