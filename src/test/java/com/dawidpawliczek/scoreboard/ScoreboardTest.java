package com.dawidpawliczek.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class ScoreboardTest {

    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        scoreboard = new Scoreboard();
    }

    @Nested
    class StartMatch {

        @Test
        void startsWithScoreZeroZero() {
            Match match = scoreboard.startMatch("Mexico", "Canada");

            assertThat(match.homeTeam()).isEqualTo("Mexico");
            assertThat(match.awayTeam()).isEqualTo("Canada");
            assertThat(match.homeScore()).isZero();
            assertThat(match.awayScore()).isZero();
            assertThat(match.totalScore()).isZero();
            assertThat(match.id()).isNotNull();
        }

        @Test
        void trimsTeamNames() {
            Match match = scoreboard.startMatch("  Mexico  ", "\tCanada\n");

            assertThat(match.homeTeam()).isEqualTo("Mexico");
            assertThat(match.awayTeam()).isEqualTo("Canada");
        }

        @Test
        void allowsMultipleSimultaneousMatchesWithUniqueIds() {
            Match first = scoreboard.startMatch("Mexico", "Canada");
            Match second = scoreboard.startMatch("Spain", "Brazil");
            Match third = scoreboard.startMatch("Germany", "France");

            assertThat(List.of(first.id(), second.id(), third.id()))
                    .doesNotHaveDuplicates();
            assertThat(scoreboard.getSummary()).hasSize(3);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\t\n"})
        void rejectsNullOrBlankHomeTeam(String invalidName) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> scoreboard.startMatch(invalidName, "Canada"))
                    .withMessageContaining("homeTeam");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\t\n"})
        void rejectsNullOrBlankAwayTeam(String invalidName) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> scoreboard.startMatch("Mexico", invalidName))
                    .withMessageContaining("awayTeam");
        }

        @Test
        void rejectsTeamPlayingAgainstItself() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> scoreboard.startMatch("Mexico", " mexico "));
        }

        @Test
        void rejectsTeamAlreadyPlayingInAnotherMatch() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatExceptionOfType(AnotherMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.startMatch("MEXICO", "Brazil"))
                    .withMessageContaining("MEXICO");
            assertThatExceptionOfType(AnotherMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.startMatch("Spain", "Canada"))
                    .withMessageContaining("Canada");
        }
    }

    @Nested
    class UpdateScore {

        @Test
        void updatesScoreAndReturnsNewSnapshot() {
            Match started = scoreboard.startMatch("Mexico", "Canada");

            Match updated = scoreboard.updateScore(started.id(), 0, 5);

            assertThat(updated.id()).isEqualTo(started.id());
            assertThat(updated.homeTeam()).isEqualTo("Mexico");
            assertThat(updated.awayTeam()).isEqualTo("Canada");
            assertThat(updated.homeScore()).isZero();
            assertThat(updated.awayScore()).isEqualTo(5);
        }

        @Test
        void doesNotMutatePreviouslyReturnedSnapshot() {
            Match started = scoreboard.startMatch("Mexico", "Canada");

            scoreboard.updateScore(started.id(), 0, 5);

            assertThat(started.homeScore()).isZero();
            assertThat(started.awayScore()).isZero();
        }

        @Test
        void lastUpdateWins() {
            Match match = scoreboard.startMatch("Spain", "Brazil");

            scoreboard.updateScore(match.id(), 1, 0);
            scoreboard.updateScore(match.id(), 10, 2);

            assertThat(scoreboard.getSummary())
                    .singleElement()
                    .satisfies(m -> {
                        assertThat(m.homeScore()).isEqualTo(10);
                        assertThat(m.awayScore()).isEqualTo(2);
                    });
        }

        @Test
        void allowsLoweringScore() {
            Match match = scoreboard.startMatch("Germany", "France");
            scoreboard.updateScore(match.id(), 2, 1);

            Match corrected = scoreboard.updateScore(match.id(), 1, 1);

            assertThat(corrected.homeScore()).isEqualTo(1);
            assertThat(corrected.awayScore()).isEqualTo(1);
        }

        @ParameterizedTest
        @CsvSource({"-1, 0", "0, -1"})
        void rejectsNegativeScores(int homeScore, int awayScore) {
            Match match = scoreboard.startMatch("Mexico", "Canada");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> scoreboard.updateScore(match.id(), homeScore, awayScore))
                    .withMessageContaining("negative");
        }

        @Test
        void rejectsUnknownMatchId() {
            assertThatExceptionOfType(NoMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.updateScore(MatchId.newId(), 1, 0));
        }

        @Test
        void rejectsNullMatchId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> scoreboard.updateScore(null, 1, 0));
        }
    }

    @Nested
    class FinishMatch {

        @Test
        void removesMatchFromSummary() {
            Match finished = scoreboard.startMatch("Mexico", "Canada");
            Match remaining = scoreboard.startMatch("Spain", "Brazil");

            scoreboard.finishMatch(finished.id());

            assertThat(scoreboard.getSummary()).containsExactly(remaining);
        }

        @Test
        void returnsFinalSnapshot() {
            Match started = scoreboard.startMatch("Argentina", "Australia");
            scoreboard.updateScore(started.id(), 3, 1);

            Match finalResult = scoreboard.finishMatch(started.id());

            assertThat(finalResult.id()).isEqualTo(started.id());
            assertThat(finalResult.homeTeam()).isEqualTo("Argentina");
            assertThat(finalResult.awayTeam()).isEqualTo("Australia");
            assertThat(finalResult.homeScore()).isEqualTo(3);
            assertThat(finalResult.awayScore()).isEqualTo(1);
        }

        @Test
        void freesTeamsForANewMatch() {
            Match first = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.updateScore(first.id(), 0, 5);
            scoreboard.finishMatch(first.id());

            // case-insensitive: the same teams under different spelling may play again
            Match rematch = scoreboard.startMatch("MEXICO", "canada");

            assertThat(rematch.id()).isNotEqualTo(first.id());
            assertThat(rematch.homeScore()).isZero();
            assertThat(rematch.awayScore()).isZero();
        }

        @Test
        void rejectsUnknownMatchId() {
            assertThatExceptionOfType(NoMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.finishMatch(MatchId.newId()));
        }

        @Test
        void rejectsNullMatchId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> scoreboard.finishMatch(null));
        }

        @Test
        void rejectsDoubleFinish() {
            Match match = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch(match.id());

            assertThatExceptionOfType(NoMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.finishMatch(match.id()));
        }

        @Test
        void updateAfterFinishThrows() {
            Match match = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch(match.id());

            assertThatExceptionOfType(NoMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.updateScore(match.id(), 1, 0));
        }

        @Test
        void keepsOrderingOfRemainingMatches() {
            Match lowest = scoreboard.startMatch("Mexico", "Canada");
            Match middle = scoreboard.startMatch("Spain", "Brazil");
            Match highest = scoreboard.startMatch("Germany", "France");
            scoreboard.updateScore(lowest.id(), 1, 0);
            scoreboard.updateScore(middle.id(), 2, 1);
            scoreboard.updateScore(highest.id(), 3, 2);

            scoreboard.finishMatch(middle.id());

            assertThat(scoreboard.getSummary())
                    .extracting(Match::homeTeam)
                    .containsExactly("Germany", "Mexico");
        }
    }

    @Nested
    class Events {

        private final List<ScoreboardEvent> events = new ArrayList<>();

        @Test
        void notifiesMatchStarted() {
            scoreboard.subscribe(events::add);

            Match match = scoreboard.startMatch("Mexico", "Canada");

            assertThat(events).containsExactly(new ScoreboardEvent.MatchStarted(match));
        }

        @Test
        void notifiesScoreUpdatedWithPreviousAndCurrent() {
            Match started = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.subscribe(events::add);

            Match updated = scoreboard.updateScore(started.id(), 2, 1);

            assertThat(events).containsExactly(new ScoreboardEvent.ScoreUpdated(started, updated));
        }

        @Test
        void notifiesScoreUpdatedOnNoOpUpdate() {
            Match match = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.updateScore(match.id(), 2, 1);
            scoreboard.subscribe(events::add);

            scoreboard.updateScore(match.id(), 2, 1);

            assertThat(events)
                    .singleElement()
                    .isInstanceOfSatisfying(ScoreboardEvent.ScoreUpdated.class,
                            e -> assertThat(e.previous()).isEqualTo(e.current()));
        }

        @Test
        void notifiesMatchFinishedWithFinalScore() {
            Match match = scoreboard.startMatch("Mexico", "Canada");
            Match finalScore = scoreboard.updateScore(match.id(), 0, 5);
            scoreboard.subscribe(events::add);

            scoreboard.finishMatch(match.id());

            assertThat(events).containsExactly(new ScoreboardEvent.MatchFinished(finalScore));
        }

        @Test
        void firesAfterStateMutation() {
            List<List<Match>> summariesSeen = new ArrayList<>();
            scoreboard.subscribe(event -> summariesSeen.add(scoreboard.getSummary()));

            scoreboard.startMatch("Mexico", "Canada");

            assertThat(summariesSeen).singleElement().satisfies(
                    summary -> assertThat(summary).hasSize(1));
        }

        @Test
        void notifiesAllSubscribersInSubscriptionOrder() {
            List<String> order = new ArrayList<>();
            scoreboard.subscribe(event -> order.add("first"));
            scoreboard.subscribe(event -> order.add("second"));

            scoreboard.startMatch("Mexico", "Canada");

            assertThat(order).containsExactly("first", "second");
        }

        @Test
        void cancelStopsNotifications() {
            List<ScoreboardEvent> other = new ArrayList<>();
            Subscription subscription = scoreboard.subscribe(events::add);
            scoreboard.subscribe(other::add);

            subscription.cancel();
            subscription.cancel(); // idempotent
            scoreboard.startMatch("Mexico", "Canada");

            assertThat(events).isEmpty();
            assertThat(other).hasSize(1);
        }

        @Test
        void sameLambdaSubscribedTwiceIsNotifiedTwice() {
            Consumer<ScoreboardEvent> listener = events::add;
            scoreboard.subscribe(listener);
            Subscription second = scoreboard.subscribe(listener);

            scoreboard.startMatch("Mexico", "Canada");
            second.cancel();
            scoreboard.startMatch("Spain", "Brazil");

            assertThat(events).hasSize(3); // 2 for the first event, 1 for the second
        }

        @Test
        void failedOperationEmitsNothing() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.subscribe(events::add);

            assertThatExceptionOfType(NoMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.updateScore(MatchId.newId(), 1, 0));
            assertThatExceptionOfType(AnotherMatchInProgressException.class)
                    .isThrownBy(() -> scoreboard.startMatch("Mexico", "Brazil"));

            assertThat(events).isEmpty();
        }

        @Test
        void listenerFailureDoesNotBreakOperationOrOtherListeners() {
            record Failure(ScoreboardEvent event, RuntimeException error) {
            }
            List<Failure> failures = new ArrayList<>();
            RuntimeException boom = new RuntimeException("boom");
            scoreboard.onListenerError((event, error) -> failures.add(new Failure(event, error)));
            scoreboard.subscribe(event -> {
                throw boom;
            });
            scoreboard.subscribe(events::add);

            Match match = scoreboard.startMatch("Mexico", "Canada");

            assertThat(scoreboard.getSummary()).containsExactly(match);
            assertThat(events).containsExactly(new ScoreboardEvent.MatchStarted(match));
            assertThat(failures).containsExactly(
                    new Failure(new ScoreboardEvent.MatchStarted(match), boom));
        }

        @Test
        void listenerFailureWithoutHandlerIsSwallowed() {
            scoreboard.subscribe(event -> {
                throw new RuntimeException("boom");
            });
            scoreboard.subscribe(events::add);

            Match match = scoreboard.startMatch("Mexico", "Canada");

            assertThat(scoreboard.getSummary()).containsExactly(match);
            assertThat(events).hasSize(1);
        }

        @Test
        void subscribeAndCancelFromWithinListenerIsSafe() {
            Subscription[] self = new Subscription[1];
            self[0] = scoreboard.subscribe(event -> {
                self[0].cancel();
                scoreboard.subscribe(events::add); // takes effect from the next event
            });

            scoreboard.startMatch("Mexico", "Canada");
            assertThat(events).isEmpty();

            scoreboard.startMatch("Spain", "Brazil");
            assertThat(events).hasSize(1);
        }

        @Test
        void rejectsNullListenerAndHandler() {
            assertThatNullPointerException()
                    .isThrownBy(() -> scoreboard.subscribe(null));
            assertThatNullPointerException()
                    .isThrownBy(() -> scoreboard.onListenerError(null));
        }
    }

    @Nested
    class GetSummary {

        @Test
        void isEmptyWhenNoMatchesStarted() {
            assertThat(scoreboard.getSummary()).isEmpty();
        }

        @Test
        void containsAllStartedMatches() {
            Match first = scoreboard.startMatch("Mexico", "Canada");
            Match second = scoreboard.startMatch("Spain", "Brazil");

            assertThat(scoreboard.getSummary()).containsExactlyInAnyOrder(first, second);
        }

        @Test
        void ordersTiedMatchesByMostRecentlyStartedFirst() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.startMatch("Spain", "Brazil");
            scoreboard.startMatch("Germany", "France");

            assertThat(scoreboard.getSummary())
                    .extracting(Match::homeTeam)
                    .containsExactly("Germany", "Spain", "Mexico");
        }

        @Test
        void ordersByTotalScoreDescending() {
            Match lowScore = scoreboard.startMatch("Mexico", "Canada");
            Match highScore = scoreboard.startMatch("Spain", "Brazil");

            scoreboard.updateScore(lowScore.id(), 1, 0);
            scoreboard.updateScore(highScore.id(), 3, 2);

            assertThat(scoreboard.getSummary())
                    .extracting(Match::homeTeam)
                    .containsExactly("Spain", "Mexico");
        }

        @Test
        void tieBreakUsesStartOrderNotUpdateOrder() {
            Match older = scoreboard.startMatch("Mexico", "Canada");
            Match newer = scoreboard.startMatch("Spain", "Brazil");

            // update the older match last — must not promote it above the newer one
            scoreboard.updateScore(newer.id(), 1, 1);
            scoreboard.updateScore(older.id(), 2, 0);

            assertThat(scoreboard.getSummary())
                    .extracting(Match::homeTeam)
                    .containsExactly("Spain", "Mexico");
        }

        @Test
        void matchesSpecExampleScenario() {
            Match mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
            Match spainBrazil = scoreboard.startMatch("Spain", "Brazil");
            Match germanyFrance = scoreboard.startMatch("Germany", "France");
            Match uruguayItaly = scoreboard.startMatch("Uruguay", "Italy");
            Match argentinaAustralia = scoreboard.startMatch("Argentina", "Australia");

            scoreboard.updateScore(mexicoCanada.id(), 0, 5);
            scoreboard.updateScore(spainBrazil.id(), 10, 2);
            scoreboard.updateScore(germanyFrance.id(), 2, 2);
            scoreboard.updateScore(uruguayItaly.id(), 6, 6);
            scoreboard.updateScore(argentinaAustralia.id(), 3, 1);

            assertThat(scoreboard.getSummary())
                    .extracting(Match::homeTeam, Match::homeScore, Match::awayTeam, Match::awayScore)
                    .containsExactly(
                            tuple("Uruguay", 6, "Italy", 6),
                            tuple("Spain", 10, "Brazil", 2),
                            tuple("Mexico", 0, "Canada", 5),
                            tuple("Argentina", 3, "Australia", 1),
                            tuple("Germany", 2, "France", 2));
        }

        @Test
        void returnsImmutableList() {
            scoreboard.startMatch("Mexico", "Canada");
            List<Match> summary = scoreboard.getSummary();

            assertThatThrownBy(summary::clear)
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void returnsSnapshotUnaffectedByLaterChanges() {
            scoreboard.startMatch("Mexico", "Canada");
            List<Match> summary = scoreboard.getSummary();

            scoreboard.startMatch("Spain", "Brazil");

            assertThat(summary).hasSize(1);
            assertThat(scoreboard.getSummary()).hasSize(2);
        }
    }
}
