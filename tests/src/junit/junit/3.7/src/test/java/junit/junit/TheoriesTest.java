/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TheoriesTest {
    @Test
    public void runsTheoriesWithDataPointMembersAndCustomSupplier() {
        TheoryFixture.dataPointInvocationCount = 0;
        TheoryFixture.suppliedParameterInvocationCount = 0;

        Result result = new JUnitCore().run(TheoryFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(3, TheoryFixture.dataPointInvocationCount);
        assertEquals(1, TheoryFixture.suppliedParameterInvocationCount);
    }

    @RunWith(Theories.class)
    public static class TheoryFixture {
        private static int dataPointInvocationCount;
        private static int suppliedParameterInvocationCount;

        @DataPoint
        public static final int FIELD_DATA_POINT = 1;

        @DataPoints
        public static int[] methodDataPoints() {
            return new int[] {2, 3};
        }

        @Theory
        public void acceptsDataPoints(int value) {
            assertTrue(value > 0);
            dataPointInvocationCount++;
        }

        @Theory
        public void acceptsSuppliedParameter(@ParametersSuppliedBy(TextSupplier.class) String value) {
            assertEquals("supplied", value);
            suppliedParameterInvocationCount++;
        }
    }

    public static class TextSupplier extends ParameterSupplier {
        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            return Collections.singletonList(PotentialAssignment.forValue("text", "supplied"));
        }
    }
}
