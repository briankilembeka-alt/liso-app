import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { setup, signIn, type TestEnv } from "./helpers.js";
import { autoCompleteDue } from "../src/modules/deals.js";

let env: TestEnv;
beforeAll(async () => { env = await setup(); });
afterAll(async () => { await env.close(); });

describe("sign-in", () => {
  it("rejects a wrong code and locks after 5 tries", async () => {
    await env.app.inject({ method: "POST", url: "/auth/otp", payload: { phone: "0700000001" } });
    for (let i = 0; i < 5; i++) {
      const r = await env.app.inject({ method: "POST", url: "/auth/verify", payload: { phone: "0700000001", code: "000000" } });
      expect(r.statusCode).toBe(400);
    }
    const real = /\b(\d{6})\b/.exec(env.ctx.sms.sent.at(-1)!.text)![1];
    const r = await env.app.inject({ method: "POST", url: "/auth/verify", payload: { phone: "0700000001", code: real } });
    expect(r.statusCode).toBe(400);
  });

  it("signs in, sets Luganda, and rejects a bad token", async () => {
    const u = await signIn(env, "0772 000 002");
    const me = await env.app.inject({ method: "PATCH", url: "/me", headers: u.headers, payload: { displayName: "Nakato", language: "lg" } });
    expect(me.json()).toMatchObject({ phone: "+256772000002", language: "lg", display_name: "Nakato" });
    const bad = await env.app.inject({ method: "GET", url: "/me", headers: { authorization: "Bearer nope" } });
    expect(bad.statusCode).toBe(401);
  });
});

describe("catalog", () => {
  it("serves categories in Luganda", async () => {
    const r = await env.app.inject({ method: "GET", url: "/categories?lang=lg" });
    const phones = r.json().categories.find((c: { id: string }) => c.id === "phones");
    expect(phones.name).toBe("Amasimu");
  });
});

describe("buyer calls seller and they make a deal", () => {
  it("runs the whole milestone-1 flow", async () => {
    const seller = await signIn(env, "0701111111");
    const buyer = await signIn(env, "0752222222");
    const inject = (method: "GET" | "POST", url: string, who: { headers: Record<string, string> }, payload?: object) =>
      env.app.inject({ method, url, headers: who.headers, payload });

    // Seller opens a shop in Phones, near Kikuubo.
    const shop = await inject("POST", "/sellers", seller, {
      businessName: "Kikuubo Phones", kind: "business", district: "Kampala", lat: 0.3136, lng: 32.5747, categories: ["phones-smartphones"],
    });
    expect(shop.statusCode).toBe(201);
    const sellerId = shop.json().id;
    expect(shop.json().documentsDueAt).not.toBeNull(); // no URSB/TIN yet: 30-day grace

    // Buyer finds them under the parent category.
    const list = await inject("GET", "/sellers?category=phones&lat=0.33&lng=32.58", buyer);
    expect(list.json().sellers[0]).toMatchObject({ id: sellerId, sponsored: false, canTakeCalls: true });

    // First Call asks for ID.
    const noId = await inject("POST", "/calls", buyer, { sellerId });
    expect(noId.json().error.code).toBe("ID_VERIFICATION_REQUIRED");
    await inject("POST", "/me/id-verification", buyer, { nationalIdNumber: "CM12345678ABCD" });

    const call = await inject("POST", "/calls", buyer, { sellerId });
    expect(call.statusCode).toBe(201);
    const callId = call.json().id;

    const incoming = await inject("GET", `/sellers/${sellerId}/calls/incoming`, seller);
    expect(incoming.json().calls.map((c: { id: string }) => c.id)).toEqual([callId]);
    expect((await inject("GET", `/sellers/${sellerId}/calls/incoming`, buyer)).statusCode).toBe(403);

    const waiting = await inject("GET", `/calls/${callId}`, buyer);
    expect(waiting.json()).toMatchObject({ status: "requested", role: "buyer" });
    const stranger = await signIn(env, "0785555555");
    expect((await inject("GET", `/calls/${callId}`, stranger)).statusCode).toBe(403);

    const accepted = await inject("POST", `/calls/${callId}/accept`, seller);
    expect(accepted.json().join.publish).toEqual({ width: 640, height: 360, maxBitrateKbps: 600 });
    expect((await inject("GET", `/calls/${callId}`, buyer)).json().status).toBe("active");
    const joined = await inject("POST", `/calls/${callId}/join`, buyer);
    expect(joined.json().join.token).toContain("buyer");
    await inject("POST", `/calls/${callId}/end`, buyer);

    // Seller writes up the deal; buyer accepts.
    const deal = await inject("POST", "/deals", seller, { callId, item: "Tecno Spark 20, 128 GB", priceUgx: 450000, delivery: "safe_boda" });
    expect(deal.statusCode).toBe(201);
    expect(deal.json()).toMatchObject({ feeUgx: "13500", sellerReceivesUgx: "436500", status: "agreed" });
    expect(deal.json().handoverCode).toBeUndefined(); // seller must not see it
    const dealId = deal.json().id;
    expect((await inject("POST", `/deals/${dealId}/accept`, seller)).statusCode).toBe(403);
    const acc = await inject("POST", `/deals/${dealId}/accept`, buyer);
    const code = acc.json().handoverCode as string;
    expect(code).toMatch(/^\d{4}$/);

    // Wrong code at handover is refused; the right one marks it delivered.
    const wrong = await inject("POST", `/deals/${dealId}/delivered`, seller, { handoverCode: code === "0000" ? "1111" : "0000" });
    expect(wrong.json().error.code).toBe("BAD_HANDOVER_CODE");
    const delivered = await inject("POST", `/deals/${dealId}/delivered`, seller, { handoverCode: code });
    expect(delivered.json().status).toBe("delivered");

    // Buyer stays silent; after 48 h the deal completes on its own.
    env.clock.advance(47 * 3600_000);
    expect(await autoCompleteDue(env.ctx)).toEqual([]);
    env.clock.advance(2 * 3600_000);
    expect(await autoCompleteDue(env.ctx)).toEqual([dealId]);

    const rec = await inject("GET", `/deals/${dealId}`, buyer);
    expect(rec.json().status).toBe("completed");
    expect(rec.json().record.intact).toBe(true);
    expect(rec.json().record.events.map((e: { type: string }) => e.type)).toEqual([
      "deal.agreed", "deal.buyer_accepted", "deal.delivered", "deal.auto_completed",
    ]);

    // The record can't be edited, even directly in the database.
    await expect(env.ctx.db.query("UPDATE deal_events SET payload = '{}' WHERE deal_id = $1", [dealId])).rejects.toThrow(/append-only/);

    // Top sellers counts call requests.
    const top = await inject("GET", "/sellers/top?category=phones-smartphones", buyer);
    expect(top.json().sellers[0].id).toBe(sellerId);
  });

  it("blocks calls to a suspended seller", async () => {
    const seller = await signIn(env, "0703333333");
    const buyer = await signIn(env, "0754444444");
    const shop = await env.app.inject({ method: "POST", url: "/sellers", headers: seller.headers, payload: {
      businessName: "Owino Shoes", kind: "individual", district: "Kampala", categories: ["shoes"] } });
    await env.ctx.db.query("UPDATE sellers SET suspended_until = $2 WHERE id = $1", [shop.json().id, new Date(env.clock.now.getTime() + 2 * 3600_000)]);
    await env.app.inject({ method: "POST", url: "/me/id-verification", headers: buyer.headers, payload: { nationalIdNumber: "CF98765432WXYZ" } });
    const r = await env.app.inject({ method: "POST", url: "/calls", headers: buyer.headers, payload: { sellerId: shop.json().id } });
    expect(r.json().error.code).toBe("SELLER_SUSPENDED");
  });
});
