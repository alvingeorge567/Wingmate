import { describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { createSuggester, type ClaudeClient } from "../src/claude.js";
import { buildUserMessage, SYSTEM_PROMPT } from "../src/prompt.js";
import { SuggestRequestSchema } from "../src/schema.js";

const TOKEN = "test-token";

function fakeClient(response: object) {
  const parse = vi.fn().mockResolvedValue(response);
  return { client: { messages: { parse } } as unknown as ClaudeClient, parse };
}

const okResponse = {
  stop_reason: "end_turn",
  parsed_output: {
    read: " They're curious and relaxed. ",
    suggestions: [
      { text: " burning pasta is a talent tbh ", tone: "playful" },
      { text: "what's your go-to climbing spot?", tone: "curious" },
      { text: "", tone: "sincere" },
      { text: "extra", tone: "flirty" },
    ],
  },
};

const body = {
  messages: [
    { from: "them", text: "haha what do you do for fun" },
    { from: "me", text: "climbing and burning pasta" },
    { from: "them", text: "lmao same on the pasta" },
  ],
  count: 3,
};

function post(app: ReturnType<typeof createApp>, payload: unknown, token = TOKEN) {
  return app.request("/v1/suggest", {
    method: "POST",
    headers: { "content-type": "application/json", "x-app-token": token },
    body: JSON.stringify(payload),
  });
}

describe("POST /v1/suggest", () => {
  it("returns trimmed, non-empty suggestions capped at count", async () => {
    const { client, parse } = fakeClient(okResponse);
    const app = createApp({ suggest: createSuggester({ client, model: "m" }), appToken: TOKEN });
    const res = await post(app, body);
    expect(res.status).toBe(200);
    const json = await res.json();
    expect(json.read).toBe("They're curious and relaxed.");
    expect(json.suggestions).toEqual([
      { text: "burning pasta is a talent tbh", tone: "playful" },
      { text: "what's your go-to climbing spot?", tone: "curious" },
      { text: "extra", tone: "flirty" },
    ]);

    const args = parse.mock.calls[0][0];
    expect(args.model).toBe("m");
    expect(args.output_config.effort).toBe("low");
    expect(args.system[0].text).toBe(SYSTEM_PROMPT);
    expect(args.messages[0].content).toContain("THEM: lmao same on the pasta");
  });

  it("rejects a wrong token", async () => {
    const { client } = fakeClient(okResponse);
    const app = createApp({ suggest: createSuggester({ client }), appToken: TOKEN });
    expect((await post(app, body, "nope")).status).toBe(401);
  });

  it("validates the body", async () => {
    const { client, parse } = fakeClient(okResponse);
    const app = createApp({ suggest: createSuggester({ client }), appToken: TOKEN });
    const res = await post(app, { messages: [] });
    expect(res.status).toBe(400);
    expect(parse).not.toHaveBeenCalled();
  });

  it("maps refusals to 502", async () => {
    const { client } = fakeClient({ stop_reason: "refusal", parsed_output: null });
    const app = createApp({ suggest: createSuggester({ client }), appToken: TOKEN });
    const res = await post(app, body);
    expect(res.status).toBe(502);
    expect((await res.json()).retryable).toBe(false);
  });

  it("rate limits per client", async () => {
    const { client } = fakeClient(okResponse);
    const app = createApp({ suggest: createSuggester({ client }), appToken: TOKEN, rateLimitPerMin: 2 });
    expect((await post(app, body)).status).toBe(200);
    expect((await post(app, body)).status).toBe(200);
    expect((await post(app, body)).status).toBe(429);
  });
});

describe("buildUserMessage", () => {
  const base = SuggestRequestSchema.parse(body);

  it("includes draft and profile when present", () => {
    const msg = buildUserMessage({ ...base, draft: "omg same", userProfile: "nurse, 29" });
    expect(msg).toContain("<my_draft>omg same</my_draft>");
    expect(msg).toContain("<about_me>nurse, 29</about_me>");
  });

  it("asks for a follow-up when the last message is mine", () => {
    const msg = buildUserMessage({ ...base, messages: [{ from: "me", text: "hey!" }] });
    expect(msg).toContain("haven't replied yet");
  });

  it("applies a single tone when requested", () => {
    expect(buildUserMessage({ ...base, tone: "flirty" })).toContain("All options should be flirty");
  });
});
