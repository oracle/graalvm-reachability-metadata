/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitInternalRunnersClassRoadieTest {
    @Test
    void invokesClassLevelBeforeAndAfterMethodsWithLegacyRunner() {
        LegacyClassFixture.beforeClassCalls = 0;
        LegacyClassFixture.afterClassCalls = 0;
        LegacyClassFixture.testCalls = 0;
        LegacyClassFixture.beforeRanBeforeTest = false;
        LegacyClassFixture.afterRanAfterTest = false;

        Result result = JUnitCore.runClasses(LegacyClassFixture.class);

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(LegacyClassFixture.beforeClassCalls).isEqualTo(1);
        assertThat(LegacyClassFixture.afterClassCalls).isEqualTo(1);
        assertThat(LegacyClassFixture.testCalls).isEqualTo(1);
        assertThat(LegacyClassFixture.beforeRanBeforeTest).isTrue();
        assertThat(LegacyClassFixture.afterRanAfterTest).isTrue();
    }

    @RunWith(JUnit4ClassRunner.class)
    public static class LegacyClassFixture {
        private static int beforeClassCalls;
        private static int afterClassCalls;
        private static int testCalls;
        private static boolean beforeRanBeforeTest;
        private static boolean afterRanAfterTest;

        @BeforeClass
        public static void beforeClass() {
            beforeClassCalls++;
        }

        @org.junit.Test
        public void testMethod() {
            testCalls++;
            beforeRanBeforeTest = beforeClassCalls == 1;
        }

        @AfterClass
        public static void afterClass() {
            afterClassCalls++;
            afterRanAfterTest = testCalls == 1;
        }
    }
}
