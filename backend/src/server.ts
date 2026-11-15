import { loadConfig } from "./config.js";
import { createDb } from "./db.js";
import { migrate } from "./migrate.js";
import { buildApp } from "./app.js";
import { ConsoleSms } from "./providers/sms.js";
import { FakeVideo, IvsVideo } from "./providers/video.js";
import { autoCompleteDue } from "./modules/deals.js";
import type { Ctx } from "./context.js";

const config = loadConfig();
const db = createDb(config.DATABASE_URL);
await migrate(db);

const ctx: Ctx = {
  db,
  sms: new ConsoleSms(),
  video: config.VIDEO_PROVIDER === "ivs" ? new IvsVideo(config.AWS_REGION) : new FakeVideo(),
  now: () => new Date(),
  fixedOtp: config.TEST_OTP_CODE,
};

const app = buildApp(ctx, { logger: true });
if (config.TEST_OTP_CODE) app.log.warn("TEST_OTP_CODE is set: every sign-in code is fixed. Test servers only.");
setInterval(() => {
  autoCompleteDue(ctx).catch((err) => app.log.error(err, "auto-complete failed"));
}, 5 * 60_000).unref();

await app.listen({ port: config.PORT, host: "0.0.0.0" });
