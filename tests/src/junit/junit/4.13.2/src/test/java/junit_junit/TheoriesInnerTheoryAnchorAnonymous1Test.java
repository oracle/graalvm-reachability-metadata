/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class TheoriesInnerTheoryAnchorAnonymous1Test {

    @Test
    void createsTheoryFixturesWithConstructorArguments() {
        Result result = JUnitCore.runClasses(ConstructorTheoryFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(ConstructorTheoryFixture.observedTotal).isEqualTo(3);
    }

    @RunWith(Theories.class)
    public static class ConstructorTheoryFixture {
        private static int observedTotal;
        private final int value;

        @DataPoints
        public static final int[] VALUES = {1, 2};

        public ConstructorTheoryFixture(int value) {
            this.value = value;
        }

        @Theory
        public void recordsConstructorDataPoint() {
            observedTotal += value;
        }
    }
}
