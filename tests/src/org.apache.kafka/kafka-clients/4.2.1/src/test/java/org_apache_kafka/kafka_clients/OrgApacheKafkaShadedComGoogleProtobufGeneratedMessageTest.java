/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageTest {

    @Test
    void fieldAccessorTableReflectsGeneratedMessageAccessors() {
        FieldDescriptor countField = SampleMessage.getDescriptor().findFieldByName("count");
        SampleMessage message = SampleMessage.newBuilder()
                .setCount(7)
                .build();

        assertThat(message.hasField(countField)).isTrue();
        assertThat(message.getField(countField)).isEqualTo(7);

        SampleMessage.Builder builder = message.toBuilder();
        builder.setField(countField, 11);
        assertThat(builder.hasField(countField)).isTrue();
        assertThat(builder.getField(countField)).isEqualTo(11);

        builder.clearField(countField);
        assertThat(builder.hasField(countField)).isFalse();
        assertThat(builder.build().getCount()).isZero();
    }

    public static final class SampleMessage extends GeneratedMessage {
        private static final long serialVersionUID = 1L;
        private static final SampleMessage DEFAULT_INSTANCE = new SampleMessage();
        private static final FileDescriptor FILE_DESCRIPTOR;
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private int count;
        private boolean hasCount;

        static {
            try {
                FILE_DESCRIPTOR = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = FILE_DESCRIPTOR.findMessageTypeByName("SampleMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR,
                        new String[] {"Count"},
                        SampleMessage.class,
                        Builder.class);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private SampleMessage() {
        }

        private SampleMessage(Builder builder) {
            super(builder);
            this.count = builder.count;
            this.hasCount = builder.hasCount;
        }

        public static Descriptor getDescriptor() {
            return DESCRIPTOR;
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
        public Parser<? extends GeneratedMessage> getParserForType() {
            throw new UnsupportedOperationException("Parsing is not needed by this generated-message fixture.");
        }

        @Override
        public Builder newBuilderForType() {
            return newBuilder();
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessage.BuilderParent parent) {
            return new Builder(parent);
        }

        @Override
        public Builder toBuilder() {
            Builder builder = newBuilder();
            if (hasCount()) {
                builder.setCount(getCount());
            }
            return builder;
        }

        @Override
        public SampleMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("SampleMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("count")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_accessor_fixture.proto")
                    .setPackage("forge.kafka.clients.protobuf.generatedmessage")
                    .addMessageType(message)
                    .build();
        }

        public static final class Builder extends GeneratedMessage.Builder<Builder> {
            private int count;
            private boolean hasCount;

            private Builder() {
            }

            private Builder(GeneratedMessage.BuilderParent parent) {
                super(parent);
            }

            public boolean hasCount() {
                return hasCount;
            }

            public int getCount() {
                return count;
            }

            public Builder setCount(int count) {
                this.count = count;
                this.hasCount = true;
                onChanged();
                return this;
            }

            public Builder clearCount() {
                this.count = 0;
                this.hasCount = false;
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
                return clearCount();
            }

            @Override
            public Builder clone() {
                return newBuilder().mergeFrom(buildPartial());
            }

            @Override
            public SampleMessage build() {
                return buildPartial();
            }

            @Override
            public SampleMessage buildPartial() {
                return new SampleMessage(this);
            }

            @Override
            public Builder mergeFrom(Message message) {
                if (message instanceof SampleMessage) {
                    SampleMessage sampleMessage = (SampleMessage) message;
                    if (sampleMessage.hasCount()) {
                        setCount(sampleMessage.getCount());
                    }
                    mergeUnknownFields(sampleMessage.getUnknownFields());
                    return this;
                }
                super.mergeFrom(message);
                return this;
            }

            @Override
            public Builder mergeFrom(CodedInputStream input) throws IOException {
                throw new InvalidProtocolBufferException("Parsing is not needed by this generated-message fixture.");
            }

            @Override
            public Builder mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                    throws IOException {
                throw new InvalidProtocolBufferException("Parsing is not needed by this generated-message fixture.");
            }

            @Override
            public SampleMessage getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }
        }
    }
}
