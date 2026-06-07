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
import org.junit.runners.model.TestClass;

public class AssignmentsTest {

    @Test
    void runsTheoryWithTestClassAwareParameterSupplier() {
        Result result = JUnitCore.runClasses(TestClassAwareParameterTheory.class);

        assertThat(result.getRunCount()).isPositive();
        assertThat(result.getFailureCount()).isZero();
    }

    @RunWith(Theories.class)
    public static class TestClassAwareParameterTheory {
        @Theory
        public void receivesSuppliedParameter(
                @ParametersSuppliedBy(TestClassAwareSupplier.class) String word) {
            assertThat(word).isEqualTo("test-class-aware");
        }
    }

    public static class TestClassAwareSupplier extends ParameterSupplier {
        private final TestClass testClass;

        public TestClassAwareSupplier(TestClass testClass) {
            this.testClass = testClass;
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            assertThat(testClass.getJavaClass()).isEqualTo(TestClassAwareParameterTheory.class);
            assertThat(signature.getType()).isEqualTo(String.class);
            return Collections.singletonList(PotentialAssignment.forValue("word", "test-class-aware"));
        }
    }
}
