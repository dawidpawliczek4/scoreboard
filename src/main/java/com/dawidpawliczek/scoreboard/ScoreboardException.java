package com.dawidpawliczek.scoreboard;

/**
 * Base class for domain-state errors reported by the {@link Scoreboard}. Argument
 * bugs (null/blank names, negative scores) use the standard
 * {@link IllegalArgumentException}/{@link NullPointerException} instead.
 */
public sealed class ScoreboardException extends RuntimeException
        permits AnotherMatchInProgressException, NoMatchInProgressException {

    protected ScoreboardException(String message) {
        super(message);
    }
}
