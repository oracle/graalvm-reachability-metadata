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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RunnerProviderTest {
    @Test
    void mockitoJunitRunnerCreatesInternalRunnerAndRunsMockitoTest() {
        MockitoRunnerTarget.reset();

        Result result = JUnitCore.runClasses(MockitoRunnerTarget.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.wasSuccessful()).isTrue();
        assertThat(MockitoRunnerTarget.capturedName).isEqualTo("Mockito");
        assertThat(MockitoRunnerTarget.capturedGreeting).isEqualTo("Hello Mockito");
    }

    @RunWith(MockitoJUnitRunner.StrictStubs.class)
    public static class MockitoRunnerTarget {
        private static String capturedGreeting;
        private static String capturedName;

        @Mock
        private GreetingRepository repository;

        static void reset() {
            capturedGreeting = null;
            capturedName = null;
        }

        @org.junit.Test
        public void initializesMocksAndSupportsStubbingVerificationAndCaptors() {
            ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
            when(repository.greetingFor("Mockito")).thenReturn("Hello Mockito");

            String greeting = repository.greetingFor("Mockito");

            verify(repository).greetingFor(nameCaptor.capture());
            assertThat(Mockito.mockingDetails(repository).isMock()).isTrue();
            capturedName = nameCaptor.getValue();
            capturedGreeting = greeting;
        }
    }

    interface GreetingRepository {
        String greetingFor(String name);
    }
}
