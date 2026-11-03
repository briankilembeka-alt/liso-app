import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Tests share one Postgres database, so run files one after another.
    fileParallelism: false,
    testTimeout: 15000,
  },
});
