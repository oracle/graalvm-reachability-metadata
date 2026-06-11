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
import static org.mockito.Answers.CALLS_REAL_METHODS;

public class ConstructorInstantiatorTest {
    @Test
    void mockSettingsCanConstructClassWithConstructorArguments() {
        try {
            ConstructedService service =
                    Mockito.mock(
                            ConstructedService.class,
                            Mockito.withSettings()
                                    .mockMaker(MockMakers.SUBCLASS)
                                    .useConstructor("configured")
                                    .defaultAnswer(CALLS_REAL_METHODS));

            assertThat(service.description()).isEqualTo("configured service");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class ConstructedService {
        private final String name;

        public ConstructedService(String name) {
            this.name = name;
        }

        public String description() {
            return name + " service";
        }
    }
}
