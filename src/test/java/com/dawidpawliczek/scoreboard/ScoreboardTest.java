package com.dawidpawliczek.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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

            assertThatIllegalStateException()
                    .isThrownBy(() -> scoreboard.startMatch("MEXICO", "Brazil"))
                    .withMessageContaining("MEXICO");
            assertThatIllegalStateException()
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
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> scoreboard.updateScore(MatchId.newId(), 1, 0))
                    .withMessageContaining("no match in progress");
        }

        @Test
        void rejectsNullMatchId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> scoreboard.updateScore(null, 1, 0));
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
