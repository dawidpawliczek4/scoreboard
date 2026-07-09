package com.dawidpawliczek.scoreboard;

/**
 * Handle to an active listener registration, returned by
 * {@link Scoreboard#subscribe(java.util.function.Consumer)}.
 */
public interface Subscription {

    /**
     * Stops delivering events to the associated listener. Idempotent — cancelling
     * an already cancelled subscription has no effect.
     */
    void cancel();
}
