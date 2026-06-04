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

public class LenientCopyToolTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void subclassSpyCopiesFieldsFromSpiedInstance() throws Exception {
        exerciseSubclassSpyCreation(
                () -> {
                    final StatefulCounter original = new StatefulCounter("requests", 7);

                    final StatefulCounter spy =
                            Mockito.mock(
                                    StatefulCounter.class,
                                    withSettings()
                                            .mockMaker(MockMakers.SUBCLASS)
                                            .spiedInstance(original)
                                            .defaultAnswer(Mockito.CALLS_REAL_METHODS));

                    assertThat(spy).isNotSameAs(original);
                    assertThat(spy.snapshot()).isEqualTo("requests:7");

                    spy.increment();

                    assertThat(spy.snapshot()).isEqualTo("requests:8");
                    assertThat(original.snapshot()).isEqualTo("requests:7");
                    Mockito.verify(spy).increment();
                });
    }

    private static void exerciseSubclassSpyCreation(ThrowingRunnable exercise) throws Exception {
        try {
            exercise.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
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

    public static class StatefulCounter {
        private String name;
        private int count;

        public StatefulCounter(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String snapshot() {
            return name + ":" + count;
        }

        public void increment() {
            count++;
        }
    }
}
