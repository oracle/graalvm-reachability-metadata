/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassRoadieTest {
    @Test
    public void runsClassLevelBeforeAndAfterMethods() {
        ClassLevelFixture.events.clear();

        Result result = new JUnitCore().run(ClassLevelFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(1, result.getRunCount());
        assertEquals(Arrays.asList("before", "test", "after"), ClassLevelFixture.events);
    }

    @RunWith(JUnit4ClassRunner.class)
    public static class ClassLevelFixture {
        private static final List<String> events = new ArrayList<>();
        private static int beforeClassIndex;

        @BeforeClass
        public static void beforeClass() {
            beforeClassIndex = events.size();
            events.add("before");
        }

        @org.junit.Test
        public void testBodyRunsBetweenClassHooks() {
            Assert.assertEquals("before", events.get(beforeClassIndex));
            events.add("test");
        }

        @AfterClass
        public static void afterClass() {
            events.add("after");
        }
    }
}
