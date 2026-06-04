/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class RunnerProviderTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void mockitoJunitRunnerRunsJunitFourTests() {
        final Result result = JUnitCore.runClasses(MockitoRunnerFixture.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(result.wasSuccessful()).isTrue();
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class MockitoRunnerFixture {
        @Mock
        private Runnable runnable;

        @org.junit.Test
        public void initializesAnnotatedMocks() {
            assertThat(runnable).isNotNull();
        }
    }
}
