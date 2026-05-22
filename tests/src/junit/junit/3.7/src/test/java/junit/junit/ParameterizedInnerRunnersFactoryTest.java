/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

public class ParameterizedInnerRunnersFactoryTest {
    @Test
    void runsParameterizedTestsUsingCustomParametersRunnerFactory() {
        CountingParametersRunnerFactory.createdRunners = 0;

        Result result = JUnitCore.runClasses(CustomFactoryParameterizedCase.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(CountingParametersRunnerFactory.createdRunners).isEqualTo(2);
    }

    @RunWith(Parameterized.class)
    @UseParametersRunnerFactory(CountingParametersRunnerFactory.class)
    public static final class CustomFactoryParameterizedCase {
        private final String name;
        private final int value;

        public CustomFactoryParameterizedCase(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Parameters(name = "{index}: {0}={1}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] { { "one", 1 }, { "two", 2 } });
        }

        @org.junit.Test
        public void receivesConstructorParameters() {
            assertThat(name).isNotBlank();
            assertThat(value).isPositive();
        }
    }

    public static final class CountingParametersRunnerFactory implements ParametersRunnerFactory {
        private static int createdRunners;

        private final BlockJUnit4ClassRunnerWithParametersFactory delegate =
                new BlockJUnit4ClassRunnerWithParametersFactory();

        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            createdRunners++;
            return delegate.createRunnerForTestWithParameters(test);
        }
    }
}
