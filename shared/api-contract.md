# Suggestion API contract

Both keyboards (Android and iOS) call one endpoint on the backend proxy.

```
POST /v1/suggest
Headers: Content-Type: application/json
         X-App-Token: <shared app token configured on the backend>
```

## Request

```json
{
  "messages": [
    { "from": "them", "text": "haha ok so what do you do for fun" },
    { "from": "me",   "text": "mostly climbing and burning pasta" }
  ],
  "draft": "",
  "tone": "playful",
  "userProfile": "29, nurse, loves bouldering and bad horror movies",
  "count": 3
}
```

| Field | Type | Notes |
|---|---|---|
| `messages` | array, 1–30 items | Oldest first. `from` is `"them"` (the match) or `"me"` (the keyboard user). Each `text` ≤ 1000 chars. |
| `draft` | string, optional | What the user has already typed. If present, suggestions continue or polish it. |
| `tone` | optional | One of `any`, `playful`, `flirty`, `sincere`, `curious`. `any` (default) gives a mix. |
| `userProfile` | string, optional, ≤ 500 chars | Facts about the user so replies stay true to them. |
| `count` | int 1–5, default 3 | Number of suggestions. |

## Response `200`

```json
{
  "suggestions": [
    { "text": "burning pasta is a skill honestly. what's your signature disaster dish?", "tone": "playful" }
  ],
  "read": "They're asking an open question and seem relaxed and curious."
}
```

`read` is a one-line read of the other person's mood, shown as a small hint above the cards.

## Errors

| Status | Body | Meaning |
|---|---|---|
| 400 | `{ "error": "invalid_request", "details": ... }` | Body failed validation |
| 401 | `{ "error": "unauthorized" }` | Missing or wrong `X-App-Token` |
| 429 | `{ "error": "rate_limited" }` | Too many requests from this token/IP |
| 502 | `{ "error": "upstream_error" }` | The Claude API call failed |

The JSON Schema for request/response lives in [`schema.json`](schema.json).
