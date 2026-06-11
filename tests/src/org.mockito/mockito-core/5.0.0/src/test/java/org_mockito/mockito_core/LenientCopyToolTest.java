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

public class LenientCopyToolTest {
    @Test
    void spyingExistingInstanceCopiesItsFieldStateToTheSpy() {
        try {
            StatefulService original = new StatefulService("copied");

            StatefulService spy =
                    Mockito.mock(
                            StatefulService.class,
                            Mockito.withSettings()
                                    .mockMaker(MockMakers.SUBCLASS)
                                    .spiedInstance(original)
                                    .defaultAnswer(Mockito.CALLS_REAL_METHODS));

            assertThat(spy.value()).isEqualTo("copied");
            Mockito.when(spy.value()).thenReturn("stubbed");
            assertThat(spy.value()).isEqualTo("stubbed");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class StatefulService {
        private final String value;

        public StatefulService(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
