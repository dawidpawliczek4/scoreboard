package com.dawidpawliczek.scoreboard;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifier of a match on the scoreboard.
 *
 * <p>Returned by {@link Scoreboard#startMatch(String, String)} (as part of {@link Match})
 * and used to address the match in subsequent operations.
 */
public record MatchId(UUID value) {

    public MatchId {
        Objects.requireNonNull(value, "value must not be null");
    }

    static MatchId newId() {
        return new MatchId(UUID.randomUUID());
    }
}
