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
    void runsTheoriesWithFieldAndMethodDataPoints() {
        Result result = JUnitCore.runClasses(FieldAndMethodDataPointsTheory.class);

        assertThat(result.getRunCount()).isPositive();
        assertThat(result.getFailureCount()).isZero();
    }

    @Test
    void runsTheoryWithCustomParameterSupplier() {
        Result result = JUnitCore.runClasses(SuppliedParameterTheory.class);

        assertThat(result.getRunCount()).isPositive();
        assertThat(result.getFailureCount()).isZero();
    }

    @RunWith(Theories.class)
    public static class FieldAndMethodDataPointsTheory {
        @DataPoint
        public static String fieldWord = "field";

        @DataPoints
        public static String[] methodWords() {
            return new String[] {"method"};
        }

        @Theory
        public void receivesStringDataPoints(String word) {
            assertThat(word).isIn("field", "method");
        }
    }

    @RunWith(Theories.class)
    public static class SuppliedParameterTheory {
        @Theory
        public void receivesSuppliedParameter(@ParametersSuppliedBy(SingleWordSupplier.class) String word) {
            assertThat(word).isEqualTo("supplied");
        }
    }

    public static class SingleWordSupplier extends ParameterSupplier {
        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            assertThat(signature.getType()).isEqualTo(String.class);
            return Collections.singletonList(PotentialAssignment.forValue("word", "supplied"));
        }
    }
}
