/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class EnclosedTest {

    @Test
    void discoversPublicEnclosedTestClasses() {
        Result result = JUnitCore.runClasses(EnclosedFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(2);
    }

    @RunWith(Enclosed.class)
    public static class EnclosedFixture {
        public static class FirstGroup {
            public FirstGroup() {
            }

            @org.junit.Test
            public void first() {
            }
        }

        public static class SecondGroup {
            public SecondGroup() {
            }

            @org.junit.Test
            public void second() {
            }
        }
    }
}
