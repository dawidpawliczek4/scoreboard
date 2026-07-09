# Live Football World Cup Scoreboard

A simple Java library that tracks live football matches and provides an ordered
summary. Plain Maven project, no runtime dependencies.

Build and test (Java 25, Maven bundled via wrapper): `./mvnw test`

## API

| # | Operation | Method |
|---|---|---|
| 1 | Start a new match | `startMatch(home, away)` → `Match` at 0–0 |
| 2 | Update the score | `updateScore(matchId, home, away)` → updated `Match` |
| 3 | Finish a match | `finishMatch(matchId)` → final `Match`, teams freed |
| 4 | Summary of matches in progress | `getSummary()` → immutable `List<Match>`, ordered |
| 5 | Feature of choice: event subscriptions | `subscribe(listener)` → `Subscription` |

Summary ordering: total score descending, ties broken by most recently started first.

## Usage

```java
Scoreboard scoreboard = new Scoreboard();

Subscription subscription = scoreboard.subscribe(event -> {
    switch (event) {
        case ScoreboardEvent.MatchStarted(Match m) -> odds.open(m);
        case ScoreboardEvent.ScoreUpdated(Match prev, Match curr) -> odds.recalculate(prev, curr);
        case ScoreboardEvent.MatchFinished(Match m) -> odds.settle(m);
    }
});

Match match = scoreboard.startMatch("Mexico", "Canada"); // 0–0
scoreboard.updateScore(match.id(), 0, 5);                // Mexico 0 – Canada 5
List<Match> summary = scoreboard.getSummary();           // immutable, ordered
Match finalResult = scoreboard.finishMatch(match.id());  // removed, teams freed
subscription.cancel();
```

## Assumptions

- **Matches are addressed by `MatchId`** (returned from `startMatch`), not by
  team-name pairs — robust against typos and inconsistent spellings.
- **Team names are case-insensitive and trimmed** for uniqueness; the original
  spelling is preserved on `Match`.
- **A team plays in at most one match at a time** (`AnotherMatchInProgressException`);
  a team cannot play itself; null/blank names are rejected.
- **Score updates are absolute pairs**, not increments — idempotent, matches feeds
  that publish complete state. Lowering is allowed (VAR / data corrections); the
  only constraint is non-negative.
- **Unknown or already finished match id → `NoMatchInProgressException`** —
  fail-fast rather than idempotent; a silent no-op would mask integration bugs.
- **Not thread-safe** — a single-threaded caller is assumed; synchronize
  externally if needed.

## Design decisions and trade-offs

- **Immutable snapshots everywhere.** `Match` is a record, every operation returns
  a fresh snapshot, `getSummary()` returns an unmodifiable list — callers cannot
  corrupt state, and held snapshots never change under them.
- **Monotonic start-order counter, not timestamps**, backs the summary tie-break —
  deterministic and testable; `Instant.now()` can collide within one tick.
- **Domain exceptions for domain states, standard exceptions for caller bugs.**
  Sealed `ScoreboardException` base with `AnotherMatchInProgressException` and
  `NoMatchInProgressException` — a type is a stable contract to catch, a message
  is not. Argument bugs (null/blank/negative) stay on standard
  `IllegalArgumentException`/`NullPointerException` (Effective Java, Item 72).
- **`LinkedHashMap` keyed by `MatchId`, sorting on read** — O(1) id lookups,
  trivially cheap sort at World Cup scale, simple writes.

## Feature of choice (requirement 5)

**Event subscriptions** — `subscribe(Consumer<ScoreboardEvent>)` notifies
listeners of every state change (`MatchStarted`, `ScoreUpdated`, `MatchFinished`).

**Why:** the natural consumers of a live scoreboard (odds engines, statistics,
push notifications) react to changes rather than poll `getSummary()` — a
subscription API turns the library from a passive store into a data source. The
sealed event hierarchy enables exhaustive pattern-matching `switch` for consumers.

- **Synchronous delivery after mutation**, on the caller's thread; failed
  operations emit nothing; asynchrony is the caller's decision.
- **Listener failures are isolated** — they never break the operation or the other
  listeners; they are routed to a handler registered via `onListenerError(...)`
  (without one they are ignored — documented trade-off).
- **`ScoreUpdated` fires on every successful update with `(previous, current)`** —
  subscribers compute the delta and filter no-ops themselves.
- **`Subscription.cancel()` is idempotent**; the same listener may subscribe
  twice; notification follows subscription order; subscribe/cancel from inside a
  listener is safe and takes effect from the next event.
