/**
 * Seller ranking on merit: category match, proximity, trust, popularity.
 * Paid "Sponsored" slots exist but are switched off for the pilot (feature flag sponsored_slots).
 */
export interface RankInput {
  id: string;
  matchesCategory: boolean;
  distanceKm: number | null; // null when either side has no location
  trustScore: number; // 0..1
  callRequests30d: number;
}

export interface Ranked<T> {
  item: T;
  score: number;
  sponsored: boolean;
}

export const WEIGHTS = { match: 0.4, proximity: 0.25, trust: 0.25, popularity: 0.1 };

/** 1 at 0 km, 0.5 at 5 km, tending to 0 further away. Unknown distance scores as neutral. */
export const proximityScore = (km: number | null) => (km === null ? 0.5 : 1 / (1 + km / 5));

export function scoreSeller(s: RankInput, maxRequests: number): number {
  const popularity = maxRequests > 0 ? s.callRequests30d / maxRequests : 0;
  return (
    WEIGHTS.match * (s.matchesCategory ? 1 : 0) +
    WEIGHTS.proximity * proximityScore(s.distanceKm) +
    WEIGHTS.trust * s.trustScore +
    WEIGHTS.popularity * popularity
  );
}

export function rankSellers<T extends RankInput>(
  sellers: T[],
  opts: { sponsoredEnabled: boolean; sponsoredIds?: Set<string>; every?: number } = { sponsoredEnabled: false },
): Ranked<T>[] {
  const maxRequests = Math.max(0, ...sellers.map((s) => s.callRequests30d));
  const organic = sellers
    .map((item) => ({ item, score: scoreSeller(item, maxRequests), sponsored: false }))
    .sort((a, b) => b.score - a.score || a.item.id.localeCompare(b.item.id));
  if (!opts.sponsoredEnabled || !opts.sponsoredIds?.size) return organic;

  // Sponsored sellers take every Nth slot (default 1 in 4), labelled, and never appear twice.
  const every = opts.every ?? 4;
  const sponsored = organic.filter((r) => opts.sponsoredIds!.has(r.item.id)).map((r) => ({ ...r, sponsored: true }));
  const rest = organic.filter((r) => !opts.sponsoredIds!.has(r.item.id));
  const out: Ranked<T>[] = [];
  while (rest.length || sponsored.length) {
    const slot = out.length + 1;
    const next = slot % every === 0 && sponsored.length ? sponsored.shift() : (rest.shift() ?? sponsored.shift());
    out.push(next!);
  }
  return out;
}

export function haversineKm(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}
