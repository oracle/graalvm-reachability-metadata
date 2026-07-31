/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockJUnit4ClassRunnerWithParametersTest {
    @Test
    void runsParameterizedTestsUsingConstructorInjection() {
        ConstructorInjectedFixture.values = null;

        Result result = JUnitCore.runClasses(ConstructorInjectedFixture.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(ConstructorInjectedFixture.values).containsExactly("first", "second");
    }

    @Test
    void runsParameterizedTestsUsingFieldInjection() {
        FieldInjectedFixture.values = null;

        Result result = JUnitCore.runClasses(FieldInjectedFixture.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(FieldInjectedFixture.values).containsExactly("first", "second");
    }

    @RunWith(Parameterized.class)
    public static class ConstructorInjectedFixture {
        private static List<String> values;
        private final String value;

        public ConstructorInjectedFixture(String value) {
            this.value = value;
        }

        @Parameterized.Parameters
        public static List<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"first"}, {"second"}});
        }

        @org.junit.Test
        public void recordsConstructorParameter() {
            if (values == null) {
                values = new ArrayList<String>();
            }
            values.add(value);
        }
    }

    @RunWith(Parameterized.class)
    public static class FieldInjectedFixture {
        private static List<String> values;

        @Parameterized.Parameter
        public String value;

        @Parameterized.Parameters
        public static List<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"first"}, {"second"}});
        }

        @org.junit.Test
        public void recordsFieldParameter() {
            if (values == null) {
                values = new ArrayList<String>();
            }
            values.add(value);
        }
    }
}
