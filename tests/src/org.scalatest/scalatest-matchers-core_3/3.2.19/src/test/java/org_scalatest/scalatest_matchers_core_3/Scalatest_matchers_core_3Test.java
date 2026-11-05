/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_matchers_core_3;

import org.junit.jupiter.api.Test;
import org.scalatest.matchers.MatchResult;

import static org.assertj.core.api.Assertions.assertThat;

public class Scalatest_matchers_core_3Test {
    @Test
    void negationReversesMatchAndFailureMessages() {
        MatchResult result = new MatchResult(false, "value was not ready", "value was ready");
        MatchResult negated = result.negated();

        assertThat(result.matches()).isFalse();
        assertThat(negated.matches()).isTrue();
        assertThat(negated.rawFailureMessage()).isEqualTo(result.rawNegatedFailureMessage());
        assertThat(negated.rawNegatedFailureMessage()).isEqualTo(result.rawFailureMessage());
        assertThat(negated.negated()).isEqualTo(result);
    }
}
