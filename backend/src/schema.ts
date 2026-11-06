import { z } from "zod";

export const TONES = ["playful", "flirty", "sincere", "curious"] as const;

export const SuggestRequestSchema = z
  .object({
    messages: z
      .array(
        z.object({
          from: z.enum(["them", "me"]),
          text: z.string().trim().min(1).max(1000),
        }),
      )
      .min(1)
      .max(30),
    draft: z.string().max(1000).optional(),
    tone: z.enum(["any", ...TONES]).optional().default("any"),
    userProfile: z.string().max(500).optional(),
    count: z.number().int().min(1).max(5).optional().default(3),
  })
  .strict();

export type SuggestRequest = z.infer<typeof SuggestRequestSchema>;

/** Shape Claude must return (enforced with structured outputs). */
export const SuggestOutputSchema = z.object({
  read: z.string(),
  suggestions: z.array(
    z.object({
      text: z.string(),
      tone: z.enum(TONES),
    }),
  ),
});

export type SuggestOutput = z.infer<typeof SuggestOutputSchema>;
