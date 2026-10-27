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
import org.junit.jupiter.api.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

@SuppressWarnings("deprecation")
public class MethodRoadieTest {

    @Test
    void invokesLegacyMethodLifecycleMethods() throws Exception {
        Fixture.events.clear();

        Result result = new JUnitCore().run(new JUnit4ClassRunner(Fixture.class));

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(Fixture.events).containsExactly("before", "test", "after");
    }

    public static class Fixture {
        private static final List<String> events = new ArrayList<>();

        public Fixture() {
        }

        @Before
        public void before() {
            events.add("before");
        }

        @org.junit.Test
        public void execute() {
            events.add("test");
        }

        @After
        public void after() {
            events.add("after");
        }
    }
}
