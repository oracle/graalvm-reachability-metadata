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

import org.junit.After;
import org.junit.Before;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class MethodRoadieTest {
    @Test
    void invokesInstanceLifecycleMethodsAroundOldJUnit4RunnerTestMethod() {
        MethodRoadieDrivenTestCase.events.clear();

        Result result = JUnitCore.runClasses(MethodRoadieDrivenTestCase.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(MethodRoadieDrivenTestCase.events).containsExactly("before", "test", "after");
    }

    @RunWith(JUnit4ClassRunner.class)
    public static final class MethodRoadieDrivenTestCase {
        private static final List<String> events = new ArrayList<>();

        @Before
        public void before() {
            events.add("before");
        }

        @org.junit.Test
        public void testBody() {
            events.add("test");
        }

        @After
        public void after() {
            events.add("after");
        }
    }
}
