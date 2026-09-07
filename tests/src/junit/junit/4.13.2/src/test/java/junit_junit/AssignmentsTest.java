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
    void constructsNoArgAndTestClassAwareParameterSuppliers() {
        Result result = JUnitCore.runClasses(SupplierFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(SupplierFixture.noArgValue).isEqualTo("plain");
        assertThat(SupplierFixture.contextValue).isEqualTo("SupplierFixture");
    }

    @RunWith(Theories.class)
    public static class SupplierFixture {
        private static String noArgValue;
        private static String contextValue;

        @Theory
        public void usesNoArgSupplier(@ParametersSuppliedBy(NoArgSupplier.class) String value) {
            noArgValue = value;
        }

        @Theory
        public void usesContextSupplier(@ParametersSuppliedBy(ContextSupplier.class) String value) {
            contextValue = value;
        }
    }

    public static class NoArgSupplier extends ParameterSupplier {
        public NoArgSupplier() {
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            return Collections.singletonList(PotentialAssignment.forValue("plain", "plain"));
        }
    }

    public static class ContextSupplier extends ParameterSupplier {
        private final String testClassName;

        public ContextSupplier(TestClass testClass) {
            testClassName = testClass.getJavaClass().getSimpleName();
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            return Collections.singletonList(PotentialAssignment.forValue("context", testClassName));
        }
    }
}
