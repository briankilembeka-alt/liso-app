import type { Db } from "./db.js";
import type { SmsProvider } from "./providers/sms.js";
import type { VideoProvider } from "./providers/video.js";

/** Everything a route needs. Tests build one with fakes. */
export interface Ctx {
  db: Db;
  sms: SmsProvider;
  video: VideoProvider;
  now: () => Date;
  /** Test servers only: use this sign-in code instead of a random one. */
  fixedOtp?: string;
}
