import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { SYSTEM_PROMPT, buildUserMessage } from "./prompt.js";
import { SuggestOutputSchema, type SuggestOutput, type SuggestRequest } from "./schema.js";

export class SuggestionError extends Error {
  constructor(
    message: string,
    readonly retryable: boolean,
  ) {
    super(message);
  }
}

/** Minimal surface we use from the SDK, so tests can pass a fake. */
export type ClaudeClient = Pick<Anthropic, "messages">;

export interface SuggesterOptions {
  client?: ClaudeClient;
  model?: string;
}

export function createSuggester(opts: SuggesterOptions = {}) {
  const client = opts.client ?? new Anthropic({ timeout: 20_000, maxRetries: 1 });
  const model = opts.model ?? process.env.SUGGEST_MODEL ?? "claude-haiku-5-5";

  return async function suggest(req: SuggestRequest): Promise<SuggestOutput> {
    let response;
    try {
      response = await client.messages.parse({
        model,
        max_tokens: 2000,
        // Keyboard UX needs speed: low effort is plenty for short chat replies.
        output_config: { effort: "low", format: zodOutputFormat(SuggestOutputSchema) },
        system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
        messages: [{ role: "user", content: buildUserMessage(req) }],
      });
    } catch (err) {
      if (err instanceof Anthropic.RateLimitError || err instanceof Anthropic.InternalServerError) {
        throw new SuggestionError(`Claude API temporarily unavailable: ${err.status}`, true);
      }
      if (err instanceof Anthropic.APIConnectionError) {
        throw new SuggestionError("Could not reach Claude API", true);
      }
      if (err instanceof Anthropic.APIError) {
        throw new SuggestionError(`Claude API error: ${err.status}`, false);
      }
      throw err;
    }

    if (response.stop_reason === "refusal") {
      throw new SuggestionError("Request declined by safety filters", false);
    }
    const out = response.parsed_output;
    if (!out || out.suggestions.length === 0) {
      throw new SuggestionError(`No suggestions returned (stop_reason=${response.stop_reason})`, true);
    }
    return {
      read: out.read.trim(),
      suggestions: out.suggestions
        .map((s) => ({ tone: s.tone, text: s.text.trim() }))
        .filter((s) => s.text.length > 0)
        .slice(0, req.count),
    };
  };
}

export type Suggester = ReturnType<typeof createSuggester>;
