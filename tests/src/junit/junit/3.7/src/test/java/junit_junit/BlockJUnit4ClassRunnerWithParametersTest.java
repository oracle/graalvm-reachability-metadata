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
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

public class BlockJUnit4ClassRunnerWithParametersTest {

    @org.junit.jupiter.api.Test
    void createsParameterizedTestsUsingConstructorInjection() {
        Result result = JUnitCore.runClasses(ConstructorInjectedParametersCase.class);

        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isZero();
    }

    @org.junit.jupiter.api.Test
    void createsParameterizedTestsUsingFieldInjection() {
        Result result = JUnitCore.runClasses(FieldInjectedParametersCase.class);

        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isZero();
    }

    @RunWith(Parameterized.class)
    public static class ConstructorInjectedParametersCase {
        private final String word;
        private final int number;

        public ConstructorInjectedParametersCase(String word, int number) {
            this.word = word;
            this.number = number;
        }

        @Parameters(name = "{index}: {0}={1}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] { {"one", 3}, {"two", 3} });
        }

        @Test
        public void receivesConstructorParameters() {
            assertThat(word.length()).isEqualTo(number);
        }
    }

    @RunWith(Parameterized.class)
    public static class FieldInjectedParametersCase {
        @Parameter(0)
        public String word;

        @Parameter(1)
        public int number;

        @Parameters(name = "{index}: {0}={1}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] { {"three", 5}, {"four", 4} });
        }

        @Test
        public void receivesFieldParameters() {
            assertThat(word.length()).isEqualTo(number);
        }
    }
}
