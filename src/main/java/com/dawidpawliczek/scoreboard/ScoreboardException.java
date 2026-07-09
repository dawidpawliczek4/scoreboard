package com.dawidpawliczek.scoreboard;

/**
 * Base class for domain-state errors reported by the {@link Scoreboard}.
 *
 * <p>Covers conflicts with the scoreboard's current state — conditions a caller can
 * meaningfully react to (e.g. skip a duplicate feed event). Plain argument bugs
 * (null/blank names, negative scores) intentionally stay on the standard
 * {@link IllegalArgumentException}/{@link NullPointerException}.
 */
public sealed class ScoreboardException extends RuntimeException
        permits AnotherMatchInProgressException, NoMatchInProgressException {

    protected ScoreboardException(String message) {
        super(message);
    }
}
