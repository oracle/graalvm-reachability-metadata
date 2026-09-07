/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.TestWithParameters;

public class ParameterizedInnerRunnersFactoryTest {

    @Test
    void instantiatesACustomParameterizedRunnerFactory() {
        Result result = JUnitCore.runClasses(ParameterizedFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(ParameterizedFixture.observed).isEqualTo("custom-factory");
    }

    @RunWith(Parameterized.class)
    @UseParametersRunnerFactory(CustomRunnerFactory.class)
    public static class ParameterizedFixture {
        private static String observed;
        private final String value;

        public ParameterizedFixture(String value) {
            this.value = value;
        }

        @Parameters
        public static Iterable<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"custom-factory"}});
        }

        @org.junit.Test
        public void execute() {
            observed = value;
        }
    }

    public static class CustomRunnerFactory implements ParametersRunnerFactory {
        public CustomRunnerFactory() {
        }

        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            return new BlockJUnit4ClassRunnerWithParameters(test);
        }
    }
}
