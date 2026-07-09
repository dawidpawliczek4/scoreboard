package com.dawidpawliczek.scoreboard;

/**
 * Thrown when starting a match with a team that is already playing in another
 * match in progress.
 */
public final class AnotherMatchInProgressException extends ScoreboardException {

    public AnotherMatchInProgressException(String team) {
        super("team is already playing in another match: " + team);
    }
}
