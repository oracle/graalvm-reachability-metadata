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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleHandlerInnerModuleSystemFoundTest {
    @Test
    void subclassMockMakerCreatesInterfaceMockThroughByteBuddyModuleHandler() {
        try {
            @SuppressWarnings("unchecked")
            List<String> names =
                    Mockito.mock(List.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));

            Mockito.when(names.get(0)).thenReturn("mockito");

            assertThat(names.get(0)).isEqualTo("mockito");
            Mockito.verify(names).get(0);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
