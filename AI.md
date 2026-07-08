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
