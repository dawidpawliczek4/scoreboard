package com.dawidpawliczek.scoreboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MatchTest {

    @Test
    void totalScoreSumsBothTeams() {
        Match match = new Match(MatchId.newId(), "Uruguay", "Italy", 6, 6);

        assertThat(match.totalScore()).isEqualTo(12);
    }

    @Test
    void rejectsNegativeScores() {
        MatchId id = MatchId.newId();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Match(id, "Mexico", "Canada", -1, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Match(id, "Mexico", "Canada", 0, -1));
    }

    @Test
    void rejectsNullFields() {
        MatchId id = MatchId.newId();

        assertThatNullPointerException()
                .isThrownBy(() -> new Match(null, "Mexico", "Canada", 0, 0));
        assertThatNullPointerException()
                .isThrownBy(() -> new Match(id, null, "Canada", 0, 0));
        assertThatNullPointerException()
                .isThrownBy(() -> new Match(id, "Mexico", null, 0, 0));
        assertThatNullPointerException()
                .isThrownBy(() -> new MatchId(null));
    }
}
