/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.withSettings;

public class InlineDelegateByteBuddyMockMakerTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void inlineMockMakerCreatesFinalClassMockUsingConstructors() throws Exception {
        exerciseInlineMockito(
                () -> {
                    final FinalGreetingService service =
                            Mockito.mock(
                                    FinalGreetingService.class,
                                    withSettings().mockMaker(MockMakers.INLINE));

                    Mockito.when(service.greeting("Mockito")).thenReturn("Hello Mockito");

                    assertThat(Mockito.mockingDetails(service).isMock()).isTrue();
                    assertThat(service.getClass()).isEqualTo(FinalGreetingService.class);
                    assertThat(service.greeting("Mockito")).isEqualTo("Hello Mockito");
                });
    }

    private static void exerciseInlineMockito(ThrowingRunnable exercise) throws Exception {
        try {
            exercise.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            final Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null) {
                throw exception;
            }
        }
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return (Error) current;
            }
            current = current.getCause();
        }
        return null;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class FinalGreetingService {
        private final String prefix;

        public FinalGreetingService(
                String prefix, int retryCount, boolean enabled, Object dependency) {
            this.prefix = prefix + retryCount + enabled + dependency;
        }

        public final String greeting(String name) {
            return prefix + name;
        }
    }
}
