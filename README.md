# Live Football World Cup Scoreboard

A simple Java library that tracks football matches in progress and provides an
ordered summary. Implemented as a plain Maven project with no runtime dependencies.

## Status

Implemented so far:

1. **Start a new match** — `Scoreboard.startMatch(homeTeam, awayTeam)`, initial score 0–0
2. **Update the score** — `Scoreboard.updateScore(matchId, homeScore, awayScore)`,
   absolute score pair
3. **Finish a match** — `Scoreboard.finishMatch(matchId)`, removes the match and
   frees both teams
4. **Get a summary of matches in progress** — `Scoreboard.getSummary()`, ordered by
   total score (descending), ties broken by most recently started match first
5. **Feature of choice: event subscriptions** — `Scoreboard.subscribe(listener)`,
   see [Feature of choice](#feature-of-choice-requirement-5)

## Usage

```java
Scoreboard scoreboard = new Scoreboard();

Match match = scoreboard.startMatch("Mexico", "Canada"); // 0–0
scoreboard.startMatch("Spain", "Brazil");

scoreboard.updateScore(match.id(), 0, 5); // Mexico 0 – Canada 5

List<Match> summary = scoreboard.getSummary(); // immutable snapshot, ordered

Match finalResult = scoreboard.finishMatch(match.id()); // removed, teams freed
```

Reacting to scoreboard events (see [Feature of choice](#feature-of-choice-requirement-5)):

```java
Subscription subscription = scoreboard.subscribe(event -> {
    switch (event) {
        case ScoreboardEvent.MatchStarted(Match m) -> odds.open(m);
        case ScoreboardEvent.ScoreUpdated(Match prev, Match curr) -> {
            if (curr.totalScore() != prev.totalScore()) odds.recalculate(curr);
        }
        case ScoreboardEvent.MatchFinished(Match m) -> odds.settle(m);
    }
});
scoreboard.onListenerError((event, error) -> log.error("listener failed for {}", event, error));
...
subscription.cancel();
```

## Building and testing

```bash
./mvnw test
```

Requires Java 25; Maven is provided by the bundled wrapper (`mvnw`).

## Assumptions

- **Matches are addressed by an identifier**, not by team-name pairs.
  `startMatch` returns an immutable `Match` snapshot carrying a `MatchId`; upcoming
  operations (`updateScore`, `finishMatch`) will take that id. This makes the
  contract robust against team-name typos and inconsistent spellings, at the cost
  of the caller having to hold on to the handle.
- **Team names are compared case-insensitively after trimming** when enforcing
  uniqueness — `"Mexico"` and `" mexico "` are the same team. The original
  (trimmed) spelling is preserved on the `Match` snapshot. This protects against
  duplicates from inconsistent upstream data.
- **A team can play in at most one match at a time.** Starting a match with a team
  that is already playing throws `AnotherMatchInProgressException`.
- **A team cannot play against itself**; null/blank team names are rejected
  (`IllegalArgumentException`).
- **Score updates are absolute, not incremental.** `updateScore` receives the full
  score pair (as in the task's example data) and replaces the current state — the
  operation is idempotent and matches feeds that publish complete state.
- **Scores may be lowered.** A VAR-revoked goal or an upstream data correction is a
  legitimate update, so the only constraint on a score is that it is non-negative.
  Guarding against out-of-order feed data is considered the data source's concern,
  not this library's.
- **Updating an unknown match id throws `NoMatchInProgressException`**; a null id
  throws `NullPointerException`.
- **Finishing a match removes it and frees both teams** — they can immediately
  start a new match (with a fresh id and a 0–0 score). `finishMatch` returns the
  final score snapshot so callers can archive it without querying first.
- **Finish is fail-fast, not idempotent.** Finishing an unknown or already
  finished match throws `NoMatchInProgressException`, consistently with
  `updateScore`. A silent no-op would be friendlier to at-least-once feeds but
  masks integration bugs; deduplication is left to the caller.
- **The scoreboard is not thread-safe.** The library assumes a single-threaded
  caller; concurrent access requires external synchronization. This keeps the
  implementation simple and is documented explicitly as a trade-off.

## Design decisions and trade-offs

- **Immutable snapshots.** `Match` is a record; the scoreboard never mutates a
  `Match` it has handed out, and `getSummary()` returns an unmodifiable list.
  Callers cannot corrupt scoreboard state, and previously returned summaries are
  unaffected by later changes.
- **Monotonic start-order counter instead of timestamps.** The "most recently
  started first" tie-break is backed by an internal sequence number assigned at
  `startMatch` time. Wall-clock timestamps (`Instant.now()`) can collide for
  matches started within the same tick and make tests non-deterministic; a counter
  is exact and fully testable without mocking a clock.
- **Domain exceptions for domain states, standard exceptions for caller bugs.**
  Conflicts with the scoreboard's state — conditions a caller can meaningfully
  react to, e.g. skipping a duplicate finish event from an at-least-once feed —
  throw dedicated types under a sealed base `ScoreboardException`:
  `AnotherMatchInProgressException` (team already playing) and
  `NoMatchInProgressException` (unknown or finished match id). Matching on a type
  is a stable contract; matching on a message is not. Plain argument bugs
  (null/blank names, negative scores) intentionally stay on the standard
  `IllegalArgumentException`/`NullPointerException` — nobody meaningfully catches
  those, and the standard types are universally understood (Effective Java,
  Item 72).
- **`LinkedHashMap` keyed by `MatchId`** as internal storage: O(1) lookup for the
  upcoming id-based operations, and predictable iteration. Sorting happens on read
  in `getSummary()` — with tens of concurrent World Cup matches, sorting on each
  read is trivially cheap and keeps writes simple.

## Feature of choice (requirement 5)

**Event subscriptions** — `subscribe(Consumer<ScoreboardEvent>)` notifies listeners
of every state change: `MatchStarted`, `ScoreUpdated`, `MatchFinished`.

**Why this feature.** This library models a live data product: the natural
consumers of a World Cup scoreboard (odds engines, statistics, push notifications)
*react to changes* rather than poll `getSummary()` in a loop. A subscription API
turns the library from a passive store into a data source, which is the actual
job of a sports data platform. The sealed `ScoreboardEvent` hierarchy also
showcases exhaustive pattern-matching `switch` on the consumer side.

Design decisions:

- **Synchronous delivery, after mutation.** Events fire on the caller's thread
  once the state change is applied — a listener querying the scoreboard during a
  notification sees the new state. Failed operations emit nothing. No threads or
  queues: the library stays single-threaded (see assumptions), and asynchrony is
  the caller's decision to make.
- **Listener failures are isolated, never lost by default.** A throwing listener
  cannot break the operation (the score *did* change — that fact is not
  negotiable) or starve the remaining listeners. Failures are routed to a handler
  registered via `onListenerError(...)`; the library has no logger of its own, so
  this hands the error to the code that does. Without a handler, failures are
  ignored (documented trade-off). `Error`s (OOM etc.) intentionally propagate.
- **`ScoreUpdated` fires on every successful update and carries
  `(previous, current)`.** The library does not guess what counts as a "change" —
  subscribers compute the delta (crucial for odds) and filter no-ops themselves.
- **`subscribe` returns a `Subscription` handle** with an idempotent `cancel()` —
  no reliance on lambda identity, and the same listener may be registered twice.
  Listeners are notified in subscription order; subscribing or cancelling from
  inside a listener is safe and takes effect from the next event.
