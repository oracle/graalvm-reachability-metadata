/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

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
    void runsPublicNestedTestClassesFromEnclosedSuite() {
        EnclosedSuite.events.clear();

        Result result = JUnitCore.runClasses(EnclosedSuite.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(2);
        assertThat(EnclosedSuite.events).containsExactlyInAnyOrder("first", "second");
    }

    @RunWith(Enclosed.class)
    public static class EnclosedSuite {
        static final List<String> events = new ArrayList<>();

        public abstract static class AbstractNestedTest {
            @org.junit.Test
            public void notRunDirectlyByEnclosed() {
                events.add("abstract");
            }
        }

        public static class FirstNestedTest {
            @org.junit.Test
            public void recordsFirstTest() {
                events.add("first");
            }
        }

        public static class SecondNestedTest {
            @org.junit.Test
            public void recordsSecondTest() {
                events.add("second");
            }
        }
    }
}
