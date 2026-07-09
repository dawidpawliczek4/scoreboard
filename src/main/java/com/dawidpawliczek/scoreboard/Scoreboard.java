package com.dawidpawliczek.scoreboard;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Live Football World Cup Scoreboard.
 *
 * <p>Tracks matches in progress and provides an ordered summary. Team names are
 * compared case-insensitively (after trimming) when enforcing uniqueness, but the
 * original spelling is preserved on the returned {@link Match} snapshots.
 *
 * <p>Listeners registered via {@link #subscribe(Consumer)} are notified of every
 * state change ({@link ScoreboardEvent}). Events are delivered synchronously on
 * the caller's thread, after the state has been mutated; a failed operation emits
 * nothing. A listener throwing never disrupts the operation or the remaining
 * listeners — failures are routed to the {@link #onListenerError(ListenerErrorHandler)}
 * handler instead.
 *
 * <p>This class is <strong>not</strong> thread-safe; callers requiring concurrent
 * access must synchronize externally.
 */
public final class Scoreboard {

    private final Map<MatchId, MatchEntry> matches = new LinkedHashMap<>();
    private final Set<String> teamsInPlay = new HashSet<>();
    private long nextStartOrder;

    private final Map<Long, Consumer<ScoreboardEvent>> listeners = new LinkedHashMap<>();
    private long nextSubscriptionId;
    private ListenerErrorHandler errorHandler = (event, error) -> {
    };

    /**
     * Starts a new match with an initial score of 0–0.
     *
     * @param homeTeam home team name
     * @param awayTeam away team name
     * @return an immutable snapshot of the newly started match
     * @throws IllegalArgumentException        if a team name is null or blank, or both names
     *                                         refer to the same team (case-insensitive, trimmed)
     * @throws AnotherMatchInProgressException if either team is already playing in another match
     */
    public Match startMatch(String homeTeam, String awayTeam) {
        String home = requireTeamName(homeTeam, "homeTeam");
        String away = requireTeamName(awayTeam, "awayTeam");
        String homeKey = normalize(home);
        String awayKey = normalize(away);
        if (homeKey.equals(awayKey)) {
            throw new IllegalArgumentException("a team cannot play against itself: " + home);
        }
        requireNotInPlay(home, homeKey);
        requireNotInPlay(away, awayKey);

        Match match = new Match(MatchId.newId(), home, away, 0, 0);
        matches.put(match.id(), new MatchEntry(match, nextStartOrder++));
        teamsInPlay.add(homeKey);
        teamsInPlay.add(awayKey);
        publish(new ScoreboardEvent.MatchStarted(match));
        return match;
    }

    /**
     * Updates the score of a match in progress to the given absolute values.
     *
     * <p>Scores may also be lowered — upstream data corrections and VAR-revoked goals
     * are legitimate; the only constraint is that scores are non-negative.
     *
     * @param matchId   id of the match to update
     * @param homeScore new absolute home team score
     * @param awayScore new absolute away team score
     * @return an immutable snapshot of the match with the updated score
     * @throws NullPointerException        if {@code matchId} is null
     * @throws NoMatchInProgressException  if no match in progress has the given id
     * @throws IllegalArgumentException    if a score is negative
     */
    public Match updateScore(MatchId matchId, int homeScore, int awayScore) {
        Objects.requireNonNull(matchId, "matchId must not be null");
        MatchEntry entry = matches.get(matchId);
        if (entry == null) {
            throw new NoMatchInProgressException(matchId);
        }
        if (homeScore < 0 || awayScore < 0) {
            throw new IllegalArgumentException(
                    "scores must not be negative: " + homeScore + "-" + awayScore);
        }

        Match current = entry.match();
        Match updated = new Match(current.id(), current.homeTeam(), current.awayTeam(), homeScore, awayScore);
        // keep the original startOrder — the summary tie-break is by start time, not update time
        matches.put(matchId, new MatchEntry(updated, entry.startOrder()));
        publish(new ScoreboardEvent.ScoreUpdated(current, updated));
        return updated;
    }

    /**
     * Finishes a match in progress: removes it from the scoreboard and frees both
     * teams to play in new matches.
     *
     * @param matchId id of the match to finish
     * @return an immutable snapshot of the match with its final score
     * @throws NullPointerException       if {@code matchId} is null
     * @throws NoMatchInProgressException if no match in progress has the given id
     *                                    (including a match that has already finished)
     */
    public Match finishMatch(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId must not be null");
        MatchEntry entry = matches.remove(matchId);
        if (entry == null) {
            throw new NoMatchInProgressException(matchId);
        }

        Match finished = entry.match();
        teamsInPlay.remove(normalize(finished.homeTeam()));
        teamsInPlay.remove(normalize(finished.awayTeam()));
        publish(new ScoreboardEvent.MatchFinished(finished));
        return finished;
    }

    /**
     * Subscribes a listener to all subsequent {@link ScoreboardEvent}s.
     *
     * <p>Listeners are notified in subscription order. The same listener instance
     * may be subscribed multiple times; each registration is independent. A
     * subscription made while an event is being delivered takes effect from the
     * next event.
     *
     * @param listener callback invoked for every event
     * @return a handle used to cancel this registration
     * @throws NullPointerException if {@code listener} is null
     */
    public Subscription subscribe(Consumer<ScoreboardEvent> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        long id = nextSubscriptionId++;
        listeners.put(id, listener);
        return () -> listeners.remove(id);
    }

    /**
     * Registers the handler invoked when a listener throws while handling an event.
     *
     * <p>Replaces the previous handler; the default one ignores failures. An
     * exception thrown by the handler itself propagates to the caller of the
     * operation that emitted the event.
     *
     * @param handler callback receiving the event and the listener's exception
     * @throws NullPointerException if {@code handler} is null
     */
    public void onListenerError(ListenerErrorHandler handler) {
        this.errorHandler = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * Returns a summary of matches in progress, ordered by total score (descending);
     * ties are broken by most recently started match first.
     *
     * @return an immutable snapshot list; never null
     */
    public List<Match> getSummary() {
        return matches.values().stream()
                .sorted(Comparator.comparingInt((MatchEntry e) -> e.match().totalScore()).reversed()
                        .thenComparing(Comparator.comparingLong(MatchEntry::startOrder).reversed()))
                .map(MatchEntry::match)
                .toList();
    }

    private static String requireTeamName(String name, String paramName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
        return name.trim();
    }

    private static String normalize(String teamName) {
        return teamName.toLowerCase(Locale.ROOT);
    }

    private void requireNotInPlay(String team, String teamKey) {
        if (teamsInPlay.contains(teamKey)) {
            throw new AnotherMatchInProgressException(team);
        }
    }

    private void publish(ScoreboardEvent event) {
        // iterate over a snapshot so listeners may subscribe/cancel during delivery;
        // such changes take effect from the next event
        for (Consumer<ScoreboardEvent> listener : List.copyOf(listeners.values())) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // a broken listener must not disrupt the operation or the other
                // listeners; Errors (OOM etc.) intentionally propagate
                errorHandler.handle(event, e);
            }
        }
    }

    /** Internal state of a tracked match; start order backs the summary tie-break. */
    private record MatchEntry(Match match, long startOrder) {
    }
}
