/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnclosedTest {
    @Test
    public void runsPublicNestedTestClasses() {
        Result result = new JUnitCore().run(EnclosedFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(2, result.getRunCount());
        assertEquals(0, result.getIgnoreCount());
    }

    @RunWith(Enclosed.class)
    public static class EnclosedFixture {
        public static class FirstNestedTest {
            @org.junit.Test
            public void succeeds() {
            }
        }

        public static class SecondNestedTest {
            @org.junit.Test
            public void alsoSucceeds() {
            }
        }

        public abstract static class AbstractNestedTest {
            @org.junit.Test
            public void wouldFailIfAbstractClassesWereIncluded() {
                throw new AssertionError("abstract nested classes must be filtered out");
            }
        }
    }
}
