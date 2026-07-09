# AI Usage

## Summary

The project is developed with **Claude Code** (Anthropic's CLI agent, model: Opus).
AI is used for design discussion, planning, code generation, and test authoring;
all key API decisions are made explicitly by me through interactive Q&A before any
code is written, and every change is reviewed before committing.

The workflow for each increment:

1. Point Claude at the task specification and the scope for the increment.
2. Claude proposes an approach and asks structured questions about open design
   decisions (with trade-offs and API previews for each option).
3. I pick the options; Claude writes a plan file which I review and approve.
4. Claude implements the code and tests, runs `mvn test`, and commits.

## Increment 1 — start match + summary (requirements 1 and 4)

### Prompt history (2026-07-07)

> *(translated from Polish)* "`ODDS and Data - JAVA Coding Task.txt` — the task
> specification. Let's start with points 1 and 4, i.e. start a new match and
> summary. Before you plan — give me proposals for how we can approach the
> solution. Remember about tests — starts with zeros, we can create multiple
> matches, returns correctly, etc."

### Design questions asked by AI and my decisions

| Question | Options considered | Decision |
|---|---|---|
| How to identify a match in the API? | `startMatch` returns a `Match` with an id vs. addressing by team-name pair | **Return `Match` with `MatchId`** — robust against name typos |
| Thread-safe from the start? | synchronized + concurrency test vs. single-threaded with documented assumption | **Single-threaded**, documented in README |
| Test stack | JUnit 5 + AssertJ vs. plain JUnit 5 | **JUnit 5 + AssertJ** |
| Team-name uniqueness | case-insensitive + trim vs. exact match | **Case-insensitive + trim**, original spelling preserved |

### Artifacts that guided the implementation

- The approved plan (structure, validation rules, sorting comparator, full test
  list) — key points mirrored in `README.md` under *Assumptions* and *Design
  decisions*.
- AI-proposed idea adopted: **monotonic start-order counter** instead of
  `Instant.now()` for the "most recently started" tie-break, for determinism and
  testability.

### What the AI generated vs. what I decided (increment 1)

- AI generated: `pom.xml` test setup, `Match`/`MatchId`/`Scoreboard`
  implementation, the test suites, this documentation.
- Human decided: increment scope (1 + 4 first), all four API decisions above,
  the requirement that tests cover 0–0 start, multiple simultaneous matches, and
  correct summary ordering.
- Human review after the increment: simplified and consolidated the generated
  validation tests in `ScoreboardTest` (merged parameterized/duplicated cases).

## Increment 2 — update score (requirement 2)

### Prompt history (2026-07-08)

> *(translated from Polish)* "Plan the implementation of point 2 — update,
> together with tests."

AI proposed the test list up front (update reflected in summary, immutability of
old snapshots, last-update-wins, negative/unknown-id validation, the full example
scenario from the spec, tie-break by start order not update order) and asked four
design questions before planning.

### Design questions asked by AI and my decisions

| Question | Options considered | Decision |
|---|---|---|
| Update semantics | absolute score pair vs. incremental goal events | **Absolute pair** — matches the spec's example data, idempotent |
| Allow lowering the score? | any value ≥ 0 vs. monotonically increasing only | **Any value ≥ 0** — VAR/data corrections are legitimate |
| Return type of `updateScore` | updated `Match` snapshot vs. `void` | **Updated `Match`** — consistent with `startMatch` |
| Exception for unknown id | `IllegalArgumentException` vs. custom `MatchNotFoundException` | **`IllegalArgumentException`** — consistent with the no-custom-hierarchy convention |

### Artifacts that guided the implementation

- The approved plan for increment 2, including the decision that `updateScore`
  replaces the `Match` snapshot while **preserving the internal start order**, so
  the summary tie-break stays "most recently *started*", never "most recently
  updated" (covered by a dedicated test).

## Increment 3 — finish match (requirement 3)

### Prompt history (2026-07-08)

> *(translated from Polish)* "For the future: do not commit after implementing.
> Now: plan the implementation of the next point."

Process feedback applied from this increment on: the AI leaves changes in the
working tree; commits are made by me after review.

AI proposed the test list up front (removal from summary, final snapshot
returned, teams freed case-insensitively with a fresh id and 0–0, unknown/null
id, double finish, update-after-finish, ordering of remaining matches) and asked
two design questions before planning.

### Design questions asked by AI and my decisions

| Question | Options considered | Decision |
|---|---|---|
| Return type of `finishMatch` | final `Match` snapshot vs. `void` | **Final `Match`** — consistent with the rest of the API, callers can archive the result |
| Unknown / already finished id | `IllegalArgumentException` (fail-fast) vs. idempotent no-op | **`IllegalArgumentException`** — consistent with `updateScore`; a no-op would mask integration bugs |

### Artifacts that guided the implementation

- The approved plan for increment 3, including the note to use
  `Map.remove(id)` directly (atomic get-and-remove, no state to roll back on
  failure) and to free teams via the existing `normalize()` helper.

## Design revision — custom domain exceptions (2026-07-09)

### Prompt history

> *(translated from Polish)* "Can we somehow get rid of `MatchTest.java` and fold
> its test cases into `ScoreboardTest.java`? Or do we need those test cases at
> all?"

AI's analysis: `totalScore()` is covered indirectly by the ordering tests, but the
`Match`/`MatchId` canonical constructors are public API (record constructors
cannot be hidden), so their invariant guards deserve coverage. AI proposed folding
them into a nested class in `ScoreboardTest`; **I rejected the merge — `MatchTest`
stays as a separate file** (test-class-per-production-class convention).

> *(translated from Polish)* "I think it's worth introducing custom exceptions.
> Give me pros and cons. I think something like `ValidationException`,
> `AnotherMatchInProgressException`, `NoMatchInProgressException` would be best."

### Pros and cons discussed

- **For**: selective catch without brittle message matching (a message-based
  assertion had already broken once in this project); separating domain states
  from programming errors; easy mapping to error codes in a wrapping service.
- **Against**: Effective Java Item 72 (prefer standard exceptions); extra public
  API surface; value exists only if callers actually catch selectively.
- AI recommended a **hybrid** and argued against my proposed `ValidationException`:
  null/blank/negative arguments are caller bugs, not domain states — nobody
  meaningfully catches them, and introducing it would force `Match`/`MatchId` to
  throw it too for consistency.

### Decision

Hybrid variant: sealed base `ScoreboardException extends RuntimeException` with
`AnotherMatchInProgressException` (start: team already playing) and
`NoMatchInProgressException` (update/finish: unknown or finished id). Argument
validation stays on `IllegalArgumentException`/`NullPointerException`;
`Match`/`MatchId` unchanged.

Also in this session: AI suggested adding the Maven wrapper so reviewers can
build without a local Maven install (`./mvnw test`).

## Increment 4 — event subscriptions (requirement 5, feature of choice)

### Prompt history (2026-07-09)

> *(translated from Polish)* "Okay, let's take care of the additional feature.
> Any proposals?"

AI proposed four candidates with trade-offs: match lookup by team (recommended by
AI), finished-match history, **score-change listeners**, and scoreboard
statistics. I picked the listeners:

> *(translated from Polish)* "Plan the implementation of the additional feature —
> score change listener. `.subscribe()` with passing a lambda that fires on every
> event."

For the listener-failure contract I asked for an in-depth trade-off analysis:

> *(translated from Polish)* "What if a listener throws an exception — what are
> the trade-offs?" … "Explain the trade-offs to me."

AI walked through four variants on a concrete two-subscriber scenario (odds +
stats modules): (1) plain propagation — first failing listener aborts
notification and escapes the operation; (2) isolate and swallow; (3) isolate,
notify all, then throw one aggregate exception with suppressed causes;
(4) isolate + user-registered error handler. My verdict on propagation: *"option
1 is IMO very mediocre"* — a broken subscriber must not starve the others or make
a succeeded update look failed. I chose **variant 4** (error handler in the API).

### Design questions asked by AI and my decisions

| Question | Options considered | Decision |
|---|---|---|
| Which feature of choice | by-team lookup / finished-match history / listeners / stats | **Listeners** — closest to a real odds/data product (push over poll) |
| Unsubscribe mechanism | `Subscription` handle vs. none vs. `unsubscribe(listener)` | **`Subscription` handle** — no reliance on lambda identity |
| Listener throws | propagate / swallow / aggregate exception / error handler | **Error handler** (`onListenerError`) — operation and other listeners always unaffected, failures land in the user's logging |
| `ScoreUpdated` semantics | every successful update with `(previous, current)` vs. only real changes with `current` | **Every update, previous+current** — subscribers compute the delta and filter no-ops |

### Artifacts that guided the implementation

- The approved plan for increment 4, including: sealed `ScoreboardEvent` with
  nested records (exhaustive pattern-matching switch for consumers), synchronous
  post-mutation delivery on the caller's thread, snapshot iteration so listeners
  can subscribe/cancel during delivery, catching `RuntimeException` only
  (`Error`s propagate), and the subscription-order = insertion-order counter
  reusing the same pattern as the summary's start-order counter.
