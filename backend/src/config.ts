import { z } from "zod";

const schema = z.object({
  DATABASE_URL: z.string().default("postgres://liso:liso@localhost:5432/liso"),
  PORT: z.coerce.number().default(8080),
  VIDEO_PROVIDER: z.enum(["fake", "ivs"]).default("fake"),
  AWS_REGION: z.string().default("eu-west-1"),
  SMS_PROVIDER: z.enum(["console"]).default("console"),
  /** Test servers only: every sign-in code is this value, so testers don't need SMS. Never set in production. */
  TEST_OTP_CODE: z.string().regex(/^\d{6}$/).optional(),
});

export type Config = z.infer<typeof schema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  return schema.parse(env);
}
