package com.dawidpawliczek.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    .withMessageContaining("Mexico");
            assertThatIllegalStateException()
                    .isThrownBy(() -> scoreboard.startMatch("Spain", "Canada"))
                    .withMessageContaining("Canada");
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
