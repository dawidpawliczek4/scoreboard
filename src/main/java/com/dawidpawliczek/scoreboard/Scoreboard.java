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
 * Live Football World Cup Scoreboard: tracks matches in progress, provides an
 * ordered summary, and notifies {@link #subscribe(Consumer) subscribed} listeners
 * of every state change.
 *
 * <p>Not thread-safe; callers requiring concurrent access must synchronize externally.
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
     * Starts a new match at 0–0 and returns its immutable snapshot. Team names are
     * trimmed; uniqueness checks are case-insensitive.
     *
     * @throws IllegalArgumentException        if a name is null/blank or both refer to the same team
     * @throws AnotherMatchInProgressException if either team is already playing
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
     * Sets the absolute score of a match in progress (lowering is allowed) and
     * returns the updated immutable snapshot.
     *
     * @throws NullPointerException       if {@code matchId} is null
     * @throws NoMatchInProgressException if no match in progress has the given id
     * @throws IllegalArgumentException   if a score is negative
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
     * Finishes a match: removes it from the scoreboard, frees both teams and
     * returns the final-score snapshot.
     *
     * @throws NullPointerException       if {@code matchId} is null
     * @throws NoMatchInProgressException if no match in progress has the given id
     *                                    (including an already finished one)
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
     * Subscribes a listener to all subsequent {@link ScoreboardEvent}s. Events are
     * delivered synchronously on the caller's thread, after the state change, in
     * subscription order; failed operations emit nothing.
     *
     * @return a handle to cancel this registration
     * @throws NullPointerException if {@code listener} is null
     */
    public Subscription subscribe(Consumer<ScoreboardEvent> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        long id = nextSubscriptionId++;
        listeners.put(id, listener);
        return () -> listeners.remove(id);
    }

    /**
     * Registers the handler for listener failures, replacing the previous one
     * (default: ignore). An exception from the handler itself propagates to the caller.
     *
     * @throws NullPointerException if {@code handler} is null
     */
    public void onListenerError(ListenerErrorHandler handler) {
        this.errorHandler = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * Returns an immutable summary of matches in progress, ordered by total score
     * (descending); ties broken by most recently started first.
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
        // snapshot: listeners may subscribe/cancel during delivery (effective from the next event)
        for (Consumer<ScoreboardEvent> listener : List.copyOf(listeners.values())) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // a broken listener must not disrupt the operation or the other listeners
                errorHandler.handle(event, e);
            }
        }
    }

    /** Internal state of a tracked match; start order backs the summary tie-break. */
    private record MatchEntry(Match match, long startOrder) {
    }
}
