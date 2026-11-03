import { describe, expect, it } from "vitest";
import { normaliseUgPhone } from "../src/lib/phone.js";
import { canonicalJson } from "../src/lib/crypto.js";
import { rankSellers, proximityScore } from "../src/modules/ranking.js";
import { feeFor } from "../src/modules/deals.js";
import { GENESIS, hashEvent, verifyChain, type DealEvent } from "../src/modules/dealLog.js";

describe("phone numbers", () => {
  it.each([
    ["0772 123 456", "+256772123456"],
    ["+256-701-234567", "+256701234567"],
    ["256752000111", "+256752000111"],
    ["772123456", "+256772123456"],
  ])("normalises %s", (input, out) => expect(normaliseUgPhone(input)).toBe(out));
  it.each(["0412 123456", "12345", "+254712345678"])("rejects %s", (input) => expect(normaliseUgPhone(input)).toBeNull());
});

describe("fee", () => {
  it("is 3% rounded to the nearest shilling", () => {
    expect(feeFor(100_000n)).toBe(3_000n);
    expect(feeFor(15_050n)).toBe(452n); // 451.5 rounds up
    expect(feeFor(1n)).toBe(0n);
  });
});

describe("canonical JSON", () => {
  it("ignores key order", () => expect(canonicalJson({ b: 1, a: { d: 2, c: [3] } })).toBe(canonicalJson({ a: { c: [3], d: 2 }, b: 1 })));
});

describe("ranking", () => {
  const base = { matchesCategory: true, distanceKm: 2, trustScore: 0.5, callRequests30d: 0 };
  it("prefers a closer, more trusted seller", () => {
    const r = rankSellers([
      { id: "far", ...base, distanceKm: 30 },
      { id: "near", ...base, trustScore: 0.9 },
    ]);
    expect(r.map((x) => x.item.id)).toEqual(["near", "far"]);
  });
  it("scores unknown distance as neutral", () => expect(proximityScore(null)).toBe(0.5));
  it("ignores sponsored slots while the flag is off", () => {
    const sellers = ["a", "b", "c", "d", "e"].map((id, i) => ({ id, ...base, trustScore: 1 - i / 10 }));
    const r = rankSellers(sellers, { sponsoredEnabled: false, sponsoredIds: new Set(["e"]) });
    expect(r.map((x) => x.item.id)).toEqual(["a", "b", "c", "d", "e"]);
    expect(r.some((x) => x.sponsored)).toBe(false);
  });
  it("puts a labelled sponsored seller in every 4th slot when switched on", () => {
    const sellers = ["a", "b", "c", "d", "e"].map((id, i) => ({ id, ...base, trustScore: 1 - i / 10 }));
    const r = rankSellers(sellers, { sponsoredEnabled: true, sponsoredIds: new Set(["e"]) });
    expect(r.map((x) => x.item.id)).toEqual(["a", "b", "c", "e", "d"]);
    expect(r[3]!.sponsored).toBe(true);
  });
});

describe("deal record chain", () => {
  function chain(n: number): DealEvent[] {
    const out: DealEvent[] = [];
    let prev = GENESIS;
    for (let seq = 1; seq <= n; seq++) {
      const e = { dealId: "d", seq, type: `t${seq}`, actorId: null, payload: { n: seq }, at: "2026-11-03T10:00:00.000Z", prevHash: prev };
      const hash = hashEvent(e);
      out.push({ ...e, hash });
      prev = hash;
    }
    return out;
  }
  it("verifies an untouched chain", () => expect(verifyChain(chain(3))).toBeNull());
  it("finds the edited event", () => {
    const c = chain(3);
    c[1]!.payload = { n: 999 };
    expect(verifyChain(c)).toBe(2);
  });
  it("finds a deleted event", () => {
    const c = chain(3);
    c.splice(1, 1);
    expect(verifyChain(c)).toBe(3);
  });
});
