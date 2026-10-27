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
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

public class BlockJUnit4ClassRunnerWithParametersTest {

    @Test
    void createsParameterizedTestsWithConstructorAndFieldInjection() {
        Result result = JUnitCore.runClasses(ConstructorFixture.class, FieldFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(ConstructorFixture.observed).isEqualTo("constructor");
        assertThat(FieldFixture.observed).isEqualTo("field");
    }

    @RunWith(Parameterized.class)
    public static class ConstructorFixture {
        private static String observed;
        private final String value;

        public ConstructorFixture(String value) {
            this.value = value;
        }

        @Parameters
        public static Iterable<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"constructor"}});
        }

        @org.junit.Test
        public void recordsConstructorParameter() {
            observed = value;
        }
    }

    @RunWith(Parameterized.class)
    public static class FieldFixture {
        private static String observed;

        @Parameter
        public String value;

        public FieldFixture() {
        }

        @Parameters
        public static Iterable<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"field"}});
        }

        @org.junit.Test
        public void recordsFieldParameter() {
            observed = value;
        }
    }
}
