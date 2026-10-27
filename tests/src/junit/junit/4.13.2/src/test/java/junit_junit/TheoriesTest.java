/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.experimental.theories.ParameterSupplier;
import org.junit.experimental.theories.ParametersSuppliedBy;
import org.junit.experimental.theories.PotentialAssignment;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class TheoriesTest {

    @Test
    void validatesAndExecutesFieldMethodAndSupplierDataPoints() {
        int numericExecutionsBeforeRun = TheoryFixture.numericExecutions;

        Result result = JUnitCore.runClasses(TheoryFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(TheoryFixture.numericExecutions).isEqualTo(numericExecutionsBeforeRun + 2);
        assertThat(TheoryFixture.suppliedValue).isEqualTo("supplied");
    }

    @RunWith(Theories.class)
    public static class TheoryFixture {
        private static int numericExecutions;
        private static String suppliedValue;

        @DataPoint
        public static final int ONE = 1;

        @DataPoints
        public static int[] moreNumbers() {
            return new int[] {2};
        }

        @Theory
        public void acceptsPositiveNumbers(int value) {
            org.junit.Assert.assertTrue(value > 0);
            numericExecutions++;
        }

        @Theory
        public void acceptsSuppliedText(@ParametersSuppliedBy(TextSupplier.class) String value) {
            suppliedValue = value;
            org.junit.Assert.assertEquals("supplied", value);
        }
    }

    public static class TextSupplier extends ParameterSupplier {
        public TextSupplier() {
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            return Collections.singletonList(PotentialAssignment.forValue("text", "supplied"));
        }
    }
}
