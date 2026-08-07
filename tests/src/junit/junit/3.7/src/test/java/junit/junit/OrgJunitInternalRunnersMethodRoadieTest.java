/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import org.junit.After;
import org.junit.Before;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitInternalRunnersMethodRoadieTest {
    @Test
    void invokesMethodLevelBeforeAndAfterMethodsWithLegacyRunner() {
        LegacyMethodFixture.events = new StringBuilder();

        Result result = JUnitCore.runClasses(LegacyMethodFixture.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(LegacyMethodFixture.events.toString()).isEqualTo("before,test,after,");
    }

    @RunWith(JUnit4ClassRunner.class)
    public static class LegacyMethodFixture {
        private static StringBuilder events = new StringBuilder();

        @Before
        public void before() {
            events.append("before,");
        }

        @org.junit.Test
        public void testMethod() {
            events.append("test,");
        }

        @After
        public void after() {
            events.append("after,");
        }
    }
}
