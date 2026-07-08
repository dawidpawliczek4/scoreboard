# Live Football World Cup Scoreboard

A simple Java library that tracks football matches in progress and provides an
ordered summary. Implemented as a plain Maven project with no runtime dependencies.

## Status

Implemented so far:

1. **Start a new match** — `Scoreboard.startMatch(homeTeam, awayTeam)`, initial score 0–0
2. **Get a summary of matches in progress** — `Scoreboard.getSummary()`, ordered by
   total score (descending), ties broken by most recently started match first

Coming next: update score, finish match, one additional operation of choice.

## Usage

```java
Scoreboard scoreboard = new Scoreboard();

Match match = scoreboard.startMatch("Mexico", "Canada"); // 0–0
scoreboard.startMatch("Spain", "Brazil");

List<Match> summary = scoreboard.getSummary(); // immutable snapshot, ordered
```

## Building and testing

```bash
mvn test
```

Requires Java 25 and Maven 3.9+.

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
  that is already playing throws `IllegalStateException`.
- **A team cannot play against itself**; null/blank team names are rejected
  (`IllegalArgumentException`).
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
- **Standard exceptions over a custom hierarchy.** Input validation failures throw
  `IllegalArgumentException`, state conflicts throw `IllegalStateException`. For a
  library of this size a custom exception hierarchy would add surface area without
  adding information.
- **`LinkedHashMap` keyed by `MatchId`** as internal storage: O(1) lookup for the
  upcoming id-based operations, and predictable iteration. Sorting happens on read
  in `getSummary()` — with tens of concurrent World Cup matches, sorting on each
  read is trivially cheap and keeps writes simple.

## Feature of choice (requirement 5)

Not implemented yet — will be introduced in a distinct commit and documented here.
