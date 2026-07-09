package com.dawidpawliczek.scoreboard;

/**
 * Thrown when an operation references a match that is not in progress — an unknown
 * id or a match that has already finished.
 */
public final class NoMatchInProgressException extends ScoreboardException {

    public NoMatchInProgressException(MatchId matchId) {
        super("no match in progress with id: " + matchId);
    }
}
