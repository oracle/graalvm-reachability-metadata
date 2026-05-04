/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ExtensionLite;
import com.google.protobuf.ExtensionRegistryLite;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryLiteTest {
    private static final String EXTENSION_REGISTRY_CLASS_NAME =
            "com.google.protobuf.ExtensionRegistry";
    private static final String GENERATED_MESSAGE_CLASS_NAME =
            "com.google.protobuf.GeneratedMessage";

    @Test
    void addingFullExtensionDelegatesToFullRegistryThroughReflection() throws Exception {
        ExtensionRegistryLite registry = newFullExtensionRegistry();
        ExtensionLite<?, ?> extension = newUninitializedFullExtension();

        assertThat(registry.getClass().getName()).isEqualTo(EXTENSION_REGISTRY_CLASS_NAME);
        try {
            registry.add(extension);
        } catch (IllegalArgumentException e) {
            assertThat(e)
                    .hasMessageContaining("Could not invoke ExtensionRegistry#add")
                    .hasCauseInstanceOf(InvocationTargetException.class);
        }
    }

    private static ExtensionRegistryLite newFullExtensionRegistry() throws Exception {
        Class<?> registryClass = Class.forName(EXTENSION_REGISTRY_CLASS_NAME);
        Method newInstance = registryClass.getMethod("newInstance");
        return (ExtensionRegistryLite) newInstance.invoke(null);
    }

    private static ExtensionLite<?, ?> newUninitializedFullExtension() throws Exception {
        Class<?> generatedMessageClass = Class.forName(GENERATED_MESSAGE_CLASS_NAME);
        Method extensionFactory = generatedMessageClass.getMethod(
                "newFileScopedGeneratedExtension",
                Class.class,
                Class.forName("com.google.protobuf.Message")
        );
        return (ExtensionLite<?, ?>) extensionFactory.invoke(null, Integer.class, null);
    }
}
