package com.dawidpawliczek.scoreboard;

/**
 * Callback invoked when a subscribed listener throws while handling an event.
 *
 * <p>Registered via {@link Scoreboard#onListenerError(ListenerErrorHandler)}. The
 * library has no logger of its own, so this is the place to surface listener
 * failures; without a registered handler they are silently ignored.
 */
@FunctionalInterface
public interface ListenerErrorHandler {

    /**
     * Handles a listener failure.
     *
     * @param event the event that was being delivered
     * @param error the exception thrown by the listener
     */
    void handle(ScoreboardEvent event, RuntimeException error);
}
