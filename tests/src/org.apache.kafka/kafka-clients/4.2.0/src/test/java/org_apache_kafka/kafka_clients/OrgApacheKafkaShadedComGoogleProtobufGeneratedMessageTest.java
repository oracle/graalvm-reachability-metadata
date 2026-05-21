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
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageTest {

    @Test
    void testFieldAccessorTableUsesGeneratedMessageReflectionAccessors() {
        FieldDescriptor countField = AccessorBackedMessage.getDescriptor().findFieldByName("count");
        AccessorBackedMessage message = AccessorBackedMessage.newBuilder()
                .setCount(7)
                .build();

        assertThat(message.hasField(countField)).isTrue();
        assertThat(message.getField(countField)).isEqualTo(7);

        AccessorBackedMessage.Builder builder = AccessorBackedMessage.newBuilder();
        builder.setField(countField, 11);

        assertThat(builder.hasField(countField)).isTrue();
        assertThat(builder.getField(countField)).isEqualTo(11);

        builder.clearField(countField);

        assertThat(builder.hasField(countField)).isFalse();
        assertThat(builder.getField(countField)).isEqualTo(0);
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class AccessorBackedMessage extends GeneratedMessage {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final AccessorBackedMessage DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("AccessorBackedMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR,
                        new String[] {"Count"},
                        AccessorBackedMessage.class,
                        Builder.class);
                DEFAULT_INSTANCE = new AccessorBackedMessage();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final boolean hasCount;
        private final int count;

        private AccessorBackedMessage() {
            hasCount = false;
            count = 0;
        }

        private AccessorBackedMessage(Builder builder) {
            super(builder);
            hasCount = builder.hasCount();
            count = builder.getCount();
        }

        public static Descriptor getDescriptor() {
            return DESCRIPTOR;
        }

        public static AccessorBackedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public boolean hasCount() {
            return hasCount;
        }

        public int getCount() {
            return count;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return unknownFields;
        }

        @Override
        public Builder newBuilderForType() {
            return newBuilder();
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            return new Builder(parent);
        }

        @Override
        public Builder toBuilder() {
            return newBuilder().mergeFrom(this);
        }

        @Override
        public AccessorBackedMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("AccessorBackedMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("count")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_accessor_test.proto")
                    .setPackage("forge.kafka.clients.generatedmessage")
                    .setSyntax("proto2")
                    .addMessageType(message)
                    .build();
        }

        public static final class Builder extends GeneratedMessage.Builder<Builder> {
            private boolean hasCount;
            private int count;

            private Builder() {
                this(null);
            }

            private Builder(BuilderParent parent) {
                super(parent);
            }

            public boolean hasCount() {
                return hasCount;
            }

            public int getCount() {
                return count;
            }

            public Builder setCount(int value) {
                hasCount = true;
                count = value;
                onChanged();
                return this;
            }

            public Builder clearCount() {
                hasCount = false;
                count = 0;
                onChanged();
                return this;
            }

            @Override
            protected FieldAccessorTable internalGetFieldAccessorTable() {
                return FIELD_ACCESSOR_TABLE;
            }

            @Override
            public Builder clear() {
                super.clear();
                hasCount = false;
                count = 0;
                return this;
            }

            @Override
            public AccessorBackedMessage build() {
                return buildPartial();
            }

            @Override
            public AccessorBackedMessage buildPartial() {
                AccessorBackedMessage result = new AccessorBackedMessage(this);
                onBuilt();
                return result;
            }

            @Override
            public AccessorBackedMessage getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }

            @Override
            public Builder mergeFrom(Message other) {
                if (other instanceof AccessorBackedMessage) {
                    return mergeFrom((AccessorBackedMessage) other);
                }
                super.mergeFrom(other);
                return this;
            }

            public Builder mergeFrom(AccessorBackedMessage other) {
                if (other == DEFAULT_INSTANCE) {
                    return this;
                }
                if (other.hasCount()) {
                    setCount(other.getCount());
                }
                mergeUnknownFields(other.getUnknownFields());
                return this;
            }
        }
    }
}
