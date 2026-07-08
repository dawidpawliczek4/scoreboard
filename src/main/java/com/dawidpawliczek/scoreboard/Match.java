package com.dawidpawliczek.scoreboard;

import java.util.Objects;

/**
 * Immutable snapshot of a match in progress.
 *
 * <p>Instances are created by {@link Scoreboard}; the scoreboard never mutates a
 * {@code Match} it has handed out, so callers can safely hold on to snapshots.
 *
 * @param id        unique identifier of the match
 * @param homeTeam  home team name (trimmed, original spelling preserved)
 * @param awayTeam  away team name (trimmed, original spelling preserved)
 * @param homeScore home team score, never negative
 * @param awayScore away team score, never negative
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
