package com.dawidpawliczek.scoreboard;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier of a match, assigned by {@link Scoreboard#startMatch(String, String)}. */
public record MatchId(UUID value) {

    public MatchId {
        Objects.requireNonNull(value, "value must not be null");
    }

    static MatchId newId() {
        return new MatchId(UUID.randomUUID());
    }
}
