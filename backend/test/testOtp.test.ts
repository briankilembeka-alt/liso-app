import { afterAll, beforeAll, expect, it } from "vitest";
import { setup, type TestEnv } from "./helpers.js";

let env: TestEnv;
beforeAll(async () => { env = await setup(); });
afterAll(async () => { await env.close(); });

it("uses the fixed test code when one is configured", async () => {
  env.ctx.fixedOtp = "246810";
  await env.app.inject({ method: "POST", url: "/auth/otp", payload: { phone: "0771234567" } });
  const r = await env.app.inject({ method: "POST", url: "/auth/verify", payload: { phone: "0771234567", code: "246810" } });
  expect(r.statusCode).toBe(200);
  expect(r.json().token).toBeTruthy();
});
