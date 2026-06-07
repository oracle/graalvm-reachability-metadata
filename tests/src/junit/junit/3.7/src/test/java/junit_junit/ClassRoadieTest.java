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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class ClassRoadieTest {

    @Test
    void invokesBeforeClassAndAfterClassMethodsAroundTestClassRun() {
        Result result = JUnitCore.runClasses(ClassFixtureCase.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(ClassFixtureCase.events).containsExactly("before", "test", "after");
    }

    @RunWith(JUnit4ClassRunner.class)
    public static class ClassFixtureCase {
        private static final List<String> events = new ArrayList<>();

        @BeforeClass
        public static void beforeClass() {
            events.clear();
            events.add("before");
        }

        @org.junit.Test
        public void testBodyRunsBetweenClassFixtures() {
            events.add("test");
        }

        @AfterClass
        public static void afterClass() {
            events.add("after");
        }
    }
}
