/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class EnclosedTest {

    @Test
    void runsPublicNestedTestClasses() {
        EnclosedSuite.executedTests.clear();

        Result result = JUnitCore.runClasses(EnclosedSuite.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(EnclosedSuite.executedTests).containsExactlyInAnyOrder("first", "second");
    }

    @RunWith(Enclosed.class)
    public static class EnclosedSuite {
        static final List<String> executedTests = new ArrayList<>();

        public static class FirstNestedCase {
            @org.junit.Test
            public void firstNestedTestRuns() {
                executedTests.add("first");
            }
        }

        public static class SecondNestedCase {
            @org.junit.Test
            public void secondNestedTestRuns() {
                executedTests.add("second");
            }
        }
    }
}
