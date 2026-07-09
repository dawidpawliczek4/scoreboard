package com.dawidpawliczek.scoreboard;

/** Handle to a listener registration, returned by {@link Scoreboard#subscribe(java.util.function.Consumer)}. */
public interface Subscription {

    /** Stops delivering events to the associated listener. Idempotent. */
    void cancel();
}
