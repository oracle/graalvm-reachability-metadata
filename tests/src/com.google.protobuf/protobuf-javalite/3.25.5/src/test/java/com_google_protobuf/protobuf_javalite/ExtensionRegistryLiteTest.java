/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Empty;
import com.google.protobuf.Extension;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryLiteTest {
    private static final String FULL_REGISTRY_CLASS_NAME = "com.google.protobuf.ExtensionRegistry";
    private static final int STRING_EXTENSION_NUMBER = 12345;

    @Test
    public void addExtensionLiteDelegatesFullRuntimeExtensionsToFullRegistry() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        ExtensionLite<Empty, String> extension = new NoOpFullRuntimeExtension();

        registry.add(extension);

        assertEquals(FULL_REGISTRY_CLASS_NAME, registry.getClass().getName());
    }

    private static final class NoOpFullRuntimeExtension extends Extension<Empty, String> {
        @Override
        public int getNumber() {
            return STRING_EXTENSION_NUMBER;
        }

        @Override
        public WireFormat.FieldType getLiteType() {
            return WireFormat.FieldType.STRING;
        }

        @Override
        public boolean isRepeated() {
            return false;
        }

        @Override
        public String getDefaultValue() {
            return "";
        }

        @Override
        public Empty getMessageDefaultInstance() {
            return null;
        }
    }
}
