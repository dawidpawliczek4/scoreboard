# AI Usage

> This is the condensed version. The full record — complete prompt history,
> every design question with the options considered, and per-increment artifacts —
> lives in [AI-full.md](AI-full.md).

## Summary

Developed with **Claude Code** (Anthropic's CLI agent, model: Opus). AI handled
design analysis, code generation, tests, and documentation; every key API decision
was made by me through structured Q&A before code was written, and every change
was reviewed by me before committing.

Workflow per increment:

1. I set the scope (one spec requirement at a time, each in its own commit).
2. AI proposes approaches and asks design questions with trade-offs and API
   previews per option; it also proposes the test list up front.
3. I pick the options; AI writes an implementation plan which I approve.
4. AI implements code + tests and runs `./mvnw test`; I review and commit.

## Key decisions (mine, from options laid out by AI)

| Area | Decision | Over the alternative of |
|---|---|---|
| Match identity | `startMatch` returns `Match` with `MatchId`; other ops take the id | addressing by team-name pair |
| Concurrency | single-threaded, documented | synchronized from the start |
| Test stack | JUnit 5 + AssertJ | plain JUnit 5 |
| Team names | case-insensitive + trim for uniqueness | exact match |
| Update semantics | absolute score pair; lowering allowed (VAR/corrections) | incremental goal events; monotonic scores |
| Return types | every operation returns a `Match` snapshot | `void` |
| Unknown/finished id | fail-fast exception | idempotent no-op |
| Exceptions | hybrid: sealed domain exceptions + standard IAE/NPE for argument bugs | my initial idea also had `ValidationException`; AI argued argument bugs are not domain states — accepted |
| Feature of choice | event subscriptions (from 4 candidates AI proposed) | by-team lookup, match history, stats |
| Listener failures | isolated + `onListenerError` handler | propagation (rejected as harmful), silent swallow, aggregate exception |
| `ScoreUpdated` | on every successful update, carries `(previous, current)` | only on real changes, current only |

## Prompt history (condensed, translated from Polish)

- *2026-07-07:* "Here's the task spec. Start with points 1 and 4. Before planning,
  give me approach proposals. Remember tests: starts at zero, multiple matches,
  correct ordering." → increment 1.
- *2026-07-08:* "Plan point 2 — update, with tests." → increment 2.
- *2026-07-08:* "For the future: do not commit after implementing." (process
  feedback — from here on I review and commit myself) "Now plan the next point."
  → increment 3.
- *2026-07-09:* "Can we drop `MatchTest.java` and fold it into `ScoreboardTest` —
  or do we need those cases at all?" → AI: record constructors are public API, the
  guards deserve coverage; I kept `MatchTest` as a separate file.
- *2026-07-09:* "I think custom exceptions are worth it — give me pros and cons.
  Maybe `ValidationException`, `AnotherMatchInProgressException`,
  `NoMatchInProgressException`." → hybrid variant chosen (see table).
- *2026-07-09:* "Add the Maven wrapper." / "Shorten README.md and AI.md — too much
  text for a recruiter."
- *2026-07-09:* "Let's do the additional feature — proposals?" then "Plan the
  score-change listener: `.subscribe()` with a lambda fired on every event." For
  the failure contract I pushed on trade-offs ("what if a listener throws?"); AI
  compared propagation / swallow / aggregate exception / error handler on a
  two-subscriber scenario; my verdict: "propagation is IMO very mediocre" → error
  handler in the API. → increment 4.

## Artifacts that guided the implementation

- Per-increment plan files (approach, file layout, validation rules, full test
  list) — approved before implementation; key points mirrored in `README.md`.
- Ideas contributed by AI and adopted: monotonic start-order counter instead of
  timestamps (deterministic tie-break), preserving start order across updates,
  atomic `Map.remove` in `finishMatch`, snapshot iteration in event delivery so
  listeners can subscribe/cancel mid-notification.
- My corrections along the way: no auto-commits, keeping `MatchTest` separate,
  consolidating generated tests into a leaner style, rejecting listener-error
  propagation, trimming this documentation.
