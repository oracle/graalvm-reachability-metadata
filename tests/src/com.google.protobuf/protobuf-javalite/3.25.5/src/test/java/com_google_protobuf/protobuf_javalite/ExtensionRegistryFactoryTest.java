/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.protobuf.ExtensionRegistryLite;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryFactoryTest {
    private static final String FULL_REGISTRY_CLASS_NAME = "com.google.protobuf.ExtensionRegistry";

    @Test
    public void publicFactoriesDelegateToAvailableFullRegistryImplementation() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        ExtensionRegistryLite emptyRegistry = ExtensionRegistryLite.getEmptyRegistry();

        assertEquals(FULL_REGISTRY_CLASS_NAME, registry.getClass().getName());
        assertEquals(FULL_REGISTRY_CLASS_NAME, emptyRegistry.getClass().getName());
        assertNotSame(emptyRegistry, registry);
        assertSame(emptyRegistry, ExtensionRegistryLite.getEmptyRegistry());
    }
}
