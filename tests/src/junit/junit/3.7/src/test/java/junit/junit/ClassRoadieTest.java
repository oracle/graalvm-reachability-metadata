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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class ClassRoadieTest {
    @Test
    void invokesClassLifecycleMethodsAroundOldJUnit4RunnerTests() {
        ClassRoadieDrivenTestCase.events.clear();

        Result result = JUnitCore.runClasses(ClassRoadieDrivenTestCase.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(ClassRoadieDrivenTestCase.events).containsExactly("before-class", "test", "after-class");
    }

    @RunWith(JUnit4ClassRunner.class)
    public static final class ClassRoadieDrivenTestCase {
        private static final List<String> events = new ArrayList<>();

        @BeforeClass
        public static void beforeClass() {
            events.add("before-class");
        }

        @org.junit.Test
        public void testBody() {
            events.add("test");
        }

        @AfterClass
        public static void afterClass() {
            events.add("after-class");
        }
    }
}
