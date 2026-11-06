import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { createSuggester } from "./claude.js";

try {
  process.loadEnvFile(".env");
} catch {
  /* no .env file: rely on real environment variables */
}

const appToken = process.env.APP_TOKEN;
if (!appToken || appToken === "change-me") {
  console.error("Set APP_TOKEN in the environment (see .env.example).");
  process.exit(1);
}

const app = createApp({
  suggest: createSuggester(),
  appToken,
  rateLimitPerMin: Number(process.env.RATE_LIMIT_PER_MIN ?? 30),
});

const port = Number(process.env.PORT ?? 8787);
serve({ fetch: app.fetch, port, hostname: "0.0.0.0" }, () =>
  console.log(`Suggestion proxy listening on http://0.0.0.0:${port}`),
);
