/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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

public class MethodRoadieTest {
    @Test
    public void junit4ClassRunnerInvokesInstanceBeforeAndAfterMethods() {
        InstanceLifecycleFixture.events.clear();

        Result result = new JUnitCore().run(InstanceLifecycleFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(1, result.getRunCount());
        assertEquals(Arrays.asList("before", "test", "after"), InstanceLifecycleFixture.events);
    }

    @RunWith(JUnit4ClassRunner.class)
    public static class InstanceLifecycleFixture {
        private static final List<String> events = new ArrayList<>();

        @Before
        public void beforeEach() {
            events.add("before");
        }

        @org.junit.Test
        public void testBodyRunsBetweenInstanceHooks() {
            Assert.assertFalse(events.isEmpty());
            Assert.assertEquals("before", events.get(events.size() - 1));
            events.add("test");
        }

        @After
        public void afterEach() {
            events.add("after");
        }
    }
}
