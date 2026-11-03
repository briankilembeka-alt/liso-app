/**
 * Normalise a Ugandan mobile number to E.164 (+2567XXXXXXXX).
 * Accepts 07XXXXXXXX, 7XXXXXXXX, 2567XXXXXXXX and +2567XXXXXXXX, with spaces or dashes.
 * Returns null for anything else.
 */
export function normaliseUgPhone(input: string): string | null {
  const d = input.replace(/[\s-]/g, "").replace(/^\+/, "");
  let local: string;
  if (/^2567\d{8}$/.test(d)) local = d.slice(3);
  else if (/^07\d{8}$/.test(d)) local = d.slice(1);
  else if (/^7\d{8}$/.test(d)) local = d;
  else return null;
  return `+256${local}`;
}
