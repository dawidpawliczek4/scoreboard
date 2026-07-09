package com.dawidpawliczek.scoreboard;

/**
 * Callback invoked when a subscribed listener throws while handling an event;
 * registered via {@link Scoreboard#onListenerError(ListenerErrorHandler)}.
 * Without one, listener failures are silently ignored.
 */
@FunctionalInterface
public interface ListenerErrorHandler {

    void handle(ScoreboardEvent event, RuntimeException error);
}
