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
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageV3Test {

    @Test
    void fieldAccessorsCallGeneratedMessageV3MethodsThroughMessageApi() {
        ReflectiveMessageV3 message = ReflectiveMessageV3.newBuilder()
                .setValue(42)
                .build();
        FieldDescriptor valueField = ReflectiveMessageV3.getDescriptor().findFieldByName("value");

        assertThat(message.hasField(valueField)).isTrue();
        assertThat(message.getField(valueField)).isEqualTo(42);
        assertThat(message.getAllFields()).containsEntry(valueField, 42);

        ReflectiveMessageV3.Builder builder = ReflectiveMessageV3.newBuilder();
        builder.setField(valueField, 7);

        assertThat(builder.hasField(valueField)).isTrue();
        assertThat(builder.getField(valueField)).isEqualTo(7);
        assertThat(builder.build().getValue()).isEqualTo(7);
    }

    @SuppressWarnings({"checkstyle:MemberName", "checkstyle:VisibilityModifier"})
    public static final class ReflectiveMessageV3 extends GeneratedMessageV3 {
        private static final FileDescriptor FILE_DESCRIPTOR;
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final ReflectiveMessageV3 DEFAULT_INSTANCE = new ReflectiveMessageV3();
        private static final long serialVersionUID = 1L;

        private int bitField0_;
        private int value_;

        static {
            try {
                FILE_DESCRIPTOR = FileDescriptor.buildFrom(
                        fileDescriptorProto(),
                        new FileDescriptor[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
            DESCRIPTOR = FILE_DESCRIPTOR.findMessageTypeByName("ReflectiveMessageV3");
            FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[] {"Value"});
        }

        private ReflectiveMessageV3() {
            unknownFields = UnknownFieldSet.getDefaultInstance();
        }

        private ReflectiveMessageV3(Builder builder) {
            super(builder);
            bitField0_ = builder.bitField0_;
            value_ = builder.value_;
        }

        public static Descriptor getDescriptor() {
            return DESCRIPTOR;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public boolean hasValue() {
            return (bitField0_ & 1) != 0;
        }

        public int getValue() {
            return value_;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE.ensureFieldAccessorsInitialized(
                    ReflectiveMessageV3.class,
                    Builder.class);
        }

        @Override
        public Builder newBuilderForType() {
            return new Builder();
        }

        @Override
        protected Builder newBuilderForType(BuilderParent parent) {
            return new Builder(parent);
        }

        @Override
        public Builder toBuilder() {
            return newBuilder().mergeFrom(this);
        }

        @Override
        public ReflectiveMessageV3 getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("ReflectiveMessageV3")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_v3_reflective_accessors_test.proto")
                    .setPackage("forge.kafka.clients.protobuf.generatedmessagev3")
                    .setSyntax("proto2")
                    .addMessageType(message)
                    .build();
        }

        @SuppressWarnings({"checkstyle:MemberName", "checkstyle:VisibilityModifier"})
        public static final class Builder extends GeneratedMessageV3.Builder<Builder> {
            private int bitField0_;
            private int value_;

            private Builder() {
            }

            private Builder(BuilderParent parent) {
                super(parent);
            }

            public boolean hasValue() {
                return (bitField0_ & 1) != 0;
            }

            public int getValue() {
                return value_;
            }

            public Builder setValue(int value) {
                value_ = value;
                bitField0_ |= 1;
                onChanged();
                return this;
            }

            public Builder clearValue() {
                value_ = 0;
                bitField0_ &= ~1;
                onChanged();
                return this;
            }

            @Override
            protected FieldAccessorTable internalGetFieldAccessorTable() {
                return FIELD_ACCESSOR_TABLE.ensureFieldAccessorsInitialized(
                        ReflectiveMessageV3.class,
                        Builder.class);
            }

            @Override
            public Builder clear() {
                super.clear();
                value_ = 0;
                bitField0_ = 0;
                return this;
            }

            @Override
            public Builder clone() {
                return newBuilder().mergeFrom(buildPartial());
            }

            @Override
            public Descriptor getDescriptorForType() {
                return DESCRIPTOR;
            }

            @Override
            public ReflectiveMessageV3 getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }

            @Override
            public ReflectiveMessageV3 build() {
                ReflectiveMessageV3 result = buildPartial();
                if (!result.isInitialized()) {
                    throw newUninitializedMessageException(result);
                }
                return result;
            }

            @Override
            public ReflectiveMessageV3 buildPartial() {
                ReflectiveMessageV3 result = new ReflectiveMessageV3(this);
                onBuilt();
                return result;
            }

            @Override
            public Builder mergeFrom(Message other) {
                if (other instanceof ReflectiveMessageV3) {
                    return mergeFrom((ReflectiveMessageV3) other);
                }
                super.mergeFrom(other);
                return this;
            }

            public Builder mergeFrom(ReflectiveMessageV3 other) {
                if (other == DEFAULT_INSTANCE) {
                    return this;
                }
                if (other.hasValue()) {
                    setValue(other.getValue());
                }
                mergeUnknownFields(other.getUnknownFields());
                onChanged();
                return this;
            }
        }
    }
}
