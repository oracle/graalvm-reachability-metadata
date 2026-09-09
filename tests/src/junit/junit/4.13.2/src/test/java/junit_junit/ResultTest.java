/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class ResultTest {

    @Test
    void summarizesASuccessfulTestRun() {
        Result result = JUnitCore.runClasses(Fixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(result.getFailures()).isEmpty();
    }

    public static class Fixture {
        public Fixture() {
        }

        @org.junit.Test
        public void execute() {
        }
    }
}
