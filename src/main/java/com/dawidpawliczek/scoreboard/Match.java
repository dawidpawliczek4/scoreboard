package com.dawidpawliczek.scoreboard;

import java.util.Objects;

/**
 * Immutable snapshot of a match. Created by {@link Scoreboard} and never mutated —
 * callers can safely hold on to it.
 */
public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore) {

    public Match {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        if (homeScore < 0 || awayScore < 0) {
            throw new IllegalArgumentException("scores must not be negative");
        }
    }

    /** Sum of both teams' scores, used for summary ordering. */
    public int totalScore() {
        return homeScore + awayScore;
    }
}
