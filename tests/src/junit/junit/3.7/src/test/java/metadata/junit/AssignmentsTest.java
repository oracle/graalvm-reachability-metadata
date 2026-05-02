/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.Assert;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssignmentsTest {
    @Test
    public void createsParameterSupplierWithTestClassConstructor() {
        TestClassConstructorTheoryFixture.suppliedFixtureNames.clear();
        TestClassAwareSupplier.constructorFixtureClass = null;

        Result result = new JUnitCore().run(TestClassConstructorTheoryFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(TestClassConstructorTheoryFixture.class, TestClassAwareSupplier.constructorFixtureClass);
        assertEquals(Collections.singletonList(TestClassConstructorTheoryFixture.class.getSimpleName()),
                TestClassConstructorTheoryFixture.suppliedFixtureNames);
    }

    @RunWith(Theories.class)
    public static class TestClassConstructorTheoryFixture {
        private static final List<String> suppliedFixtureNames = new ArrayList<>();

        @Theory
        public void consumesValue(@ParametersSuppliedBy(TestClassAwareSupplier.class) String fixtureName) {
            Assert.assertEquals(TestClassConstructorTheoryFixture.class.getSimpleName(), fixtureName);
            suppliedFixtureNames.add(fixtureName);
        }
    }

    public static class TestClassAwareSupplier extends ParameterSupplier {
        private static Class<?> constructorFixtureClass;

        private final TestClass testClass;

        public TestClassAwareSupplier(TestClass testClass) {
            this.testClass = testClass;
            constructorFixtureClass = testClass.getJavaClass();
        }

        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature signature) {
            String fixtureName = testClass.getJavaClass().getSimpleName();

            return Collections.singletonList(PotentialAssignment.forValue("fixture", fixtureName));
        }
    }
}
