/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Extension;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class ExtensionRegistryLiteTest {
    private static final String FULL_REGISTRY_CLASS_NAME = "com.google.protobuf.ExtensionRegistry";

    @Test
    void addDelegatesFullRuntimeExtensionsToFullRegistry() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();

        assertThat(registry.getClass().getName()).isEqualTo(FULL_REGISTRY_CLASS_NAME);
        assertThatNoException()
                .isThrownBy(() -> registry.add(new UnsupportedFullRuntimeExtension()));
    }

    private static final class UnsupportedFullRuntimeExtension
            extends Extension<MessageLite, Integer> {
        @Override
        public int getNumber() {
            return 12345;
        }

        @Override
        public WireFormat.FieldType getLiteType() {
            return WireFormat.FieldType.INT32;
        }

        @Override
        public boolean isRepeated() {
            return false;
        }

        @Override
        public Integer getDefaultValue() {
            return 0;
        }

        @Override
        public Message getMessageDefaultInstance() {
            return null;
        }

        @Override
        public Descriptors.FieldDescriptor getDescriptor() {
            throw new UnsupportedOperationException(
                    "PROTO1 extensions are ignored by ExtensionRegistry");
        }

        @Override
        protected ExtensionType getExtensionType() {
            return ExtensionType.PROTO1;
        }

        @Override
        protected Object fromReflectionType(Object value) {
            return value;
        }

        @Override
        protected Object singularFromReflectionType(Object value) {
            return value;
        }

        @Override
        protected Object toReflectionType(Object value) {
            return value;
        }

        @Override
        protected Object singularToReflectionType(Object value) {
            return value;
        }
    }
}
