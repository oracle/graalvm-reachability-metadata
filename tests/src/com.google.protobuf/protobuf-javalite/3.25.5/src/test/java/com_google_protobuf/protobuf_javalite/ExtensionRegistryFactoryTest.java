/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.ExtensionRegistryLite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionRegistryFactoryTest {
    @Test
    void liteFactoryUsesFullRegistryFactoryWhenFullRuntimeIsAvailable() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        ExtensionRegistryLite emptyRegistry = ExtensionRegistryLite.getEmptyRegistry();

        assertThat(registry.getClass().getName()).isEqualTo("com.google.protobuf.ExtensionRegistry");
        assertThat(emptyRegistry.getClass().getName()).isEqualTo("com.google.protobuf.ExtensionRegistry");
        assertThat(ExtensionRegistryLite.getEmptyRegistry()).isSameAs(emptyRegistry);
    }
}
