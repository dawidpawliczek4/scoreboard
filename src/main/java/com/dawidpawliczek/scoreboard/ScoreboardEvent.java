package com.dawidpawliczek.scoreboard;

import java.util.Objects;

/**
 * Event emitted by the {@link Scoreboard} to subscribed listeners. Sealed —
 * consumers can handle events with an exhaustive pattern-matching {@code switch}.
 */
public sealed interface ScoreboardEvent {

    /** A new match has been started (score 0–0). */
    record MatchStarted(Match match) implements ScoreboardEvent {
        public MatchStarted {
            Objects.requireNonNull(match, "match must not be null");
        }
    }

    /**
     * Emitted on every successful {@code updateScore}, even when the score is
     * unchanged — subscribers derive the delta from {@code previous}/{@code current}.
     */
    record ScoreUpdated(Match previous, Match current) implements ScoreboardEvent {
        public ScoreUpdated {
            Objects.requireNonNull(previous, "previous must not be null");
            Objects.requireNonNull(current, "current must not be null");
        }
    }

    /** A match has finished; {@code match} carries the final score. */
    record MatchFinished(Match match) implements ScoreboardEvent {
        public MatchFinished {
            Objects.requireNonNull(match, "match must not be null");
        }
    }
}
