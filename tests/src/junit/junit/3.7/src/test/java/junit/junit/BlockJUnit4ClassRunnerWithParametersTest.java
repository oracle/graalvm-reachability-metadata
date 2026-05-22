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
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

public class BlockJUnit4ClassRunnerWithParametersTest {
    @Test
    void runsParameterizedTestsUsingConstructorInjection() {
        Result result = JUnitCore.runClasses(ConstructorInjectedParameterizedCase.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(2);
    }

    @Test
    void runsParameterizedTestsUsingFieldInjection() {
        Result result = JUnitCore.runClasses(FieldInjectedParameterizedCase.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(2);
    }

    @RunWith(Parameterized.class)
    public static final class ConstructorInjectedParameterizedCase {
        private final String name;
        private final int value;

        public ConstructorInjectedParameterizedCase(String name, int value) {
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

    @RunWith(Parameterized.class)
    public static final class FieldInjectedParameterizedCase {
        @Parameterized.Parameter(0)
        public String name;

        @Parameterized.Parameter(1)
        public int value;

        @Parameters(name = "{index}: {0}={1}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] { { "three", 3 }, { "four", 4 } });
        }

        @org.junit.Test
        public void receivesFieldParameters() {
            assertThat(name).isNotBlank();
            assertThat(value).isPositive();
        }
    }
}
