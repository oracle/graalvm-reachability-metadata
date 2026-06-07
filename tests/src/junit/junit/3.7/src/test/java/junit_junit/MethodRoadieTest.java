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

import org.junit.After;
import org.junit.Before;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class MethodRoadieTest {

    @Test
    void invokesBeforeAndAfterMethodsAroundJUnit4TestMethod() {
        Result result = JUnitCore.runClasses(MethodFixtureCase.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(MethodFixtureCase.events).containsExactly("before", "test", "after");
    }

    @RunWith(JUnit4ClassRunner.class)
    public static class MethodFixtureCase {
        private static final List<String> events = new ArrayList<>();

        @Before
        public void before() {
            events.clear();
            events.add("before");
        }

        @org.junit.Test
        public void testBodyRunsBetweenMethodFixtures() {
            events.add("test");
        }

        @After
        public void after() {
            events.add("after");
        }
    }
}
