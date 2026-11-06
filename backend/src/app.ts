import { Hono } from "hono";
import { timingSafeEqual } from "node:crypto";
import { SuggestRequestSchema } from "./schema.js";
import { SuggestionError, type Suggester } from "./claude.js";
import { createRateLimiter } from "./rateLimit.js";

export interface AppOptions {
  suggest: Suggester;
  appToken: string;
  rateLimitPerMin?: number;
}

function tokenMatches(given: string | undefined, expected: string): boolean {
  if (!given) return false;
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export function createApp({ suggest, appToken, rateLimitPerMin = 30 }: AppOptions) {
  const app = new Hono();
  const allow = createRateLimiter(rateLimitPerMin);

  app.get("/health", (c) => c.json({ ok: true }));

  app.post("/v1/suggest", async (c) => {
    if (!tokenMatches(c.req.header("x-app-token"), appToken)) {
      return c.json({ error: "unauthorized" }, 401);
    }
    const ip = c.req.header("x-forwarded-for")?.split(",")[0]?.trim() || "local";
    if (!allow(ip)) return c.json({ error: "rate_limited" }, 429);

    let body: unknown;
    try {
      body = await c.req.json();
    } catch {
      return c.json({ error: "invalid_request", details: "Body must be JSON" }, 400);
    }
    const parsed = SuggestRequestSchema.safeParse(body);
    if (!parsed.success) {
      return c.json({ error: "invalid_request", details: parsed.error.issues }, 400);
    }

    try {
      return c.json(await suggest(parsed.data));
    } catch (err) {
      // Never log chat content: only the error message.
      console.error("suggest failed:", err instanceof Error ? err.message : err);
      const retryable = err instanceof SuggestionError && err.retryable;
      return c.json({ error: "upstream_error", retryable }, 502);
    }
  });

  return app;
}
