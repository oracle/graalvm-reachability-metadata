/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

public class ParameterizedInnerRunnersFactoryTest {

    @org.junit.jupiter.api.Test
    void createsCustomParametersRunnerFactoryDeclaredOnParameterizedTestClass() {
        RecordingParametersRunnerFactory.reset();

        Result result = JUnitCore.runClasses(UsesCustomParametersRunnerFactory.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(RecordingParametersRunnerFactory.factoryInstances).isEqualTo(1);
        assertThat(RecordingParametersRunnerFactory.runnersCreated).isEqualTo(2);
    }

    @RunWith(Parameterized.class)
    @UseParametersRunnerFactory(RecordingParametersRunnerFactory.class)
    public static class UsesCustomParametersRunnerFactory {
        private final String value;

        public UsesCustomParametersRunnerFactory(String value) {
            this.value = value;
        }

        @Parameters(name = "custom-factory-{index}: {0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] { {"alpha"}, {"bravo"} });
        }

        @Test
        public void receivesParametersFromCustomRunner() {
            assertThat(value).isNotEmpty();
        }
    }

    public static class RecordingParametersRunnerFactory implements ParametersRunnerFactory {
        static int factoryInstances;
        static int runnersCreated;

        public RecordingParametersRunnerFactory() {
            factoryInstances++;
        }

        static void reset() {
            factoryInstances = 0;
            runnersCreated = 0;
        }

        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            runnersCreated++;
            return new BlockJUnit4ClassRunnerWithParameters(test);
        }
    }
}
