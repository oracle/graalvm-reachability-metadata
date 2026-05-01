/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.protobuf.Descriptors;
import org.apache.hadoop.thirdparty.protobuf.Extension;
import org.apache.hadoop.thirdparty.protobuf.ExtensionRegistry;
import org.apache.hadoop.thirdparty.protobuf.ExtensionRegistryLite;
import org.apache.hadoop.thirdparty.protobuf.MessageLite;
import org.apache.hadoop.thirdparty.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryLiteTest {
    @Test
    void addDispatchesExtensionLiteToFullRegistryImplementation() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        RecordingProto1Extension extension = new RecordingProto1Extension();

        registry.add(extension);

        assertThat(registry).isInstanceOf(ExtensionRegistry.class);
        assertThat(extension.extensionTypeRequests()).isPositive();
    }

    private static final class RecordingProto1Extension extends Extension<MessageLite, Integer> {
        private int extensionTypeRequests;

        @Override
        public Descriptors.FieldDescriptor getDescriptor() {
            return null;
        }

        @Override
        protected ExtensionType getExtensionType() {
            extensionTypeRequests++;
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

        @Override
        public int getNumber() {
            return 321;
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
        public MessageLite getMessageDefaultInstance() {
            return null;
        }

        int extensionTypeRequests() {
            return extensionTypeRequests;
        }
    }
}
