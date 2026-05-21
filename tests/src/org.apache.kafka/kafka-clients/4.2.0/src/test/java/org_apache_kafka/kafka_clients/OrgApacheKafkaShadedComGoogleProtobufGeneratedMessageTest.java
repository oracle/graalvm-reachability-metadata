/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageTest {

    @Test
    void testGeneratedMessageFieldAccessorTableInvokesGeneratedAccessors() {
        AccessorBackedMessage message = AccessorBackedMessage.newBuilder()
                .setScalarValue(42)
                .build();

        Object reflectedValue = message.getField(AccessorBackedMessage.SCALAR_VALUE_FIELD);
        Map<FieldDescriptor, Object> allFields = message.getAllFields();

        assertThat(message.hasField(AccessorBackedMessage.SCALAR_VALUE_FIELD)).isTrue();
        assertThat(reflectedValue).isEqualTo(42);
        assertThat(allFields).containsEntry(AccessorBackedMessage.SCALAR_VALUE_FIELD, 42);
    }

    @Test
    void testGeneratedMessageBuilderFieldAccessorTableInvokesGeneratedAccessors() {
        AccessorBackedMessage.Builder builder = AccessorBackedMessage.newBuilder();

        builder.setField(AccessorBackedMessage.SCALAR_VALUE_FIELD, 7);

        assertThat(builder.hasField(AccessorBackedMessage.SCALAR_VALUE_FIELD)).isTrue();
        assertThat(builder.getField(AccessorBackedMessage.SCALAR_VALUE_FIELD)).isEqualTo(7);
        assertThat(builder.build().getScalarValue()).isEqualTo(7);

        builder.clearField(AccessorBackedMessage.SCALAR_VALUE_FIELD);

        assertThat(builder.hasField(AccessorBackedMessage.SCALAR_VALUE_FIELD)).isFalse();
        assertThat(builder.getField(AccessorBackedMessage.SCALAR_VALUE_FIELD)).isEqualTo(0);
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class AccessorBackedMessage extends GeneratedMessage {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final AccessorBackedMessage DEFAULT_INSTANCE;
        private static final FieldDescriptor SCALAR_VALUE_FIELD;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("AccessorBackedMessage");
                SCALAR_VALUE_FIELD = DESCRIPTOR.findFieldByName("scalar_value");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR,
                        new String[] {"ScalarValue"},
                        AccessorBackedMessage.class,
                        Builder.class);
                DEFAULT_INSTANCE = new AccessorBackedMessage(0, false);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final int scalarValue;
        private final boolean hasScalarValue;

        private AccessorBackedMessage(int scalarValue, boolean hasScalarValue) {
            this.scalarValue = scalarValue;
            this.hasScalarValue = hasScalarValue;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public int getScalarValue() {
            return scalarValue;
        }

        public boolean hasScalarValue() {
            return hasScalarValue;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Builder newBuilderForType() {
            return newBuilder();
        }

        @Override
        protected Builder newBuilderForType(BuilderParent parent) {
            return newBuilder();
        }

        @Override
        public Builder toBuilder() {
            return newBuilder().setScalarValue(scalarValue);
        }

        @Override
        public Message getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("AccessorBackedMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("scalar_value")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_field_accessor_table_test.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .addMessageType(message)
                    .build();
        }

        public static final class Builder extends GeneratedMessage.Builder<Builder> {
            private int scalarValue;
            private boolean hasScalarValue;

            private Builder() {
            }

            public int getScalarValue() {
                return scalarValue;
            }

            public Builder setScalarValue(int scalarValue) {
                this.scalarValue = scalarValue;
                hasScalarValue = true;
                return this;
            }

            public boolean hasScalarValue() {
                return hasScalarValue;
            }

            public Builder clearScalarValue() {
                scalarValue = 0;
                hasScalarValue = false;
                return this;
            }

            @Override
            protected FieldAccessorTable internalGetFieldAccessorTable() {
                return FIELD_ACCESSOR_TABLE;
            }

            @Override
            public Builder clone() {
                Builder builder = new Builder();
                builder.scalarValue = scalarValue;
                builder.hasScalarValue = hasScalarValue;
                return builder;
            }

            @Override
            public AccessorBackedMessage build() {
                return buildPartial();
            }

            @Override
            public AccessorBackedMessage buildPartial() {
                return new AccessorBackedMessage(scalarValue, hasScalarValue);
            }

            @Override
            public Message getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }
        }
    }
}
