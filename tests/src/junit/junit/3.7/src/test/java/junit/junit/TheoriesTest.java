/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.experimental.theories.DataPoint;
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
    void runsTheoriesWithDataPointsAndCustomParameterSupplier() {
        TheoryWithDataPointsAndSupplier.observedValues.clear();

        Result result = JUnitCore.runClasses(TheoryWithDataPointsAndSupplier.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(TheoryWithDataPointsAndSupplier.observedValues)
                .contains("field-data-point", "method-data-point", "supplied-parameter");
    }

    @RunWith(Theories.class)
    public static final class TheoryWithDataPointsAndSupplier {
        private static final List<String> observedValues = new ArrayList<>();

        @DataPoint
        public static final String FIELD_DATA_POINT = "field-data-point";

        @DataPoint
        public static String methodDataPoint() {
            return "method-data-point";
        }

        @Theory
        public void acceptsDataPoint(String value) {
            observedValues.add(value);
        }

        @Theory
        public void acceptsSuppliedValue(
                @ParametersSuppliedBy(SingleStringSupplier.class) String value) {
            observedValues.add(value);
        }
    }

    public static final class SingleStringSupplier extends ParameterSupplier {
        public SingleStringSupplier() {
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            return Collections.singletonList(PotentialAssignment.forValue("single string", "supplied-parameter"));
        }
    }
}
