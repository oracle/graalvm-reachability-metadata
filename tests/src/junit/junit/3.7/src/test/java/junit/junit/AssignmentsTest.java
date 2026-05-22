/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

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
    void runsTheoryWithParameterSupplierThatReceivesTheTestClass() {
        TheoryWithTestClassSupplier.observedValue = null;
        TestClassAwareSupplier.observedTestClass = null;

        Result result = JUnitCore.runClasses(TheoryWithTestClassSupplier.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(TestClassAwareSupplier.observedTestClass).isEqualTo(TheoryWithTestClassSupplier.class);
        assertThat(TheoryWithTestClassSupplier.observedValue).isEqualTo("supplied for TheoryWithTestClassSupplier");
    }

    @RunWith(Theories.class)
    public static final class TheoryWithTestClassSupplier {
        private static String observedValue;

        @Theory
        public void acceptsValueFromSupplier(
                @ParametersSuppliedBy(TestClassAwareSupplier.class) String value) {
            observedValue = value;
        }
    }

    public static final class TestClassAwareSupplier extends ParameterSupplier {
        private static Class<?> observedTestClass;

        private final TestClass testClass;

        public TestClassAwareSupplier(TestClass testClass) {
            this.testClass = testClass;
            observedTestClass = testClass.getJavaClass();
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            String value = "supplied for " + testClass.getJavaClass().getSimpleName();
            return Collections.singletonList(PotentialAssignment.forValue("test class name", value));
        }
    }
}
