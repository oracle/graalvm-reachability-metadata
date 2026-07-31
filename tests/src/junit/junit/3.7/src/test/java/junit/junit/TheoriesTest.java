/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

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
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TheoriesTest {
    @Test
    void validatesDataPointsAndCustomParameterSuppliers() {
        TheoryFixture.invocationCount = 0;

        Result result = JUnitCore.runClasses(TheoryFixture.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(TheoryFixture.invocationCount).isEqualTo(1);
    }

    @RunWith(Theories.class)
    public static class TheoryFixture {
        @DataPoint
        public static final String FIELD_VALUE = "field";

        @DataPoints
        public static String[] methodValues() {
            return new String[] {"method"};
        }

        private static int invocationCount;

        @Theory
        public void acceptsSuppliedValue(@ParametersSuppliedBy(SingleValueSupplier.class) String value) {
            assertThat(value).isEqualTo("supplied");
            invocationCount++;
        }
    }

    public static class SingleValueSupplier extends ParameterSupplier {
        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            return Collections.singletonList(PotentialAssignment.forValue("supplied", "supplied"));
        }
    }
}
