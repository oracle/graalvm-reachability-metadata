/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3;

import akka.protobufv3.internal.AbstractParser;
import akka.protobufv3.internal.ByteString;
import akka.protobufv3.internal.CodedInputStream;
import akka.protobufv3.internal.DescriptorProtos;
import akka.protobufv3.internal.Descriptors;
import akka.protobufv3.internal.ExtensionRegistryLite;
import akka.protobufv3.internal.GeneratedMessage;
import akka.protobufv3.internal.InvalidProtocolBufferException;
import akka.protobufv3.internal.Message;
import akka.protobufv3.internal.Parser;

public final class GeneratedMessageTestSupport {
    private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = buildFileDescriptor();
    private static final Descriptors.Descriptor MESSAGE_DESCRIPTOR =
            FILE_DESCRIPTOR.findMessageTypeByName("ReflectiveMessage");
    private static final Descriptors.FieldDescriptor FOO_FIELD = MESSAGE_DESCRIPTOR.findFieldByName("foo");
    private static final GeneratedMessage.FieldAccessorTable FIELD_ACCESSOR_TABLE =
            new GeneratedMessage.FieldAccessorTable(MESSAGE_DESCRIPTOR, new String[] {"Foo"})
                    .ensureFieldAccessorsInitialized(ReflectiveMessage.class, Builder.class);

    private GeneratedMessageTestSupport() {
    }

    public static Descriptors.FieldDescriptor fooField() {
        return FOO_FIELD;
    }

    public static ReflectiveMessage message(String foo) {
        return new ReflectiveMessage(foo);
    }

    private static Descriptors.FileDescriptor buildFileDescriptor() {
        DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("coverage/generated_message.proto")
                .setPackage("coverage.generatedmessage")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("ReflectiveMessage")
                        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                .setName("foo")
                                .setNumber(1)
                                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                                .build())
                        .build())
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final class ReflectiveMessage extends GeneratedMessage {
        private static final long serialVersionUID = 1L;
        private static final ReflectiveMessage DEFAULT_INSTANCE = new ReflectiveMessage("");
        private static final Parser<ReflectiveMessage> PARSER = new AbstractParser<ReflectiveMessage>() {
            @Override
            public ReflectiveMessage parsePartialFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                    throws InvalidProtocolBufferException {
                return DEFAULT_INSTANCE;
            }
        };

        private final String foo;

        private ReflectiveMessage(String foo) {
            this.foo = foo;
        }

        public static GeneratedMessageTestSupport.Builder newBuilder() {
            return new GeneratedMessageTestSupport.Builder();
        }

        public boolean hasFoo() {
            return !foo.isEmpty();
        }

        public String getFoo() {
            return foo;
        }

        public ByteString getFooBytes() {
            return ByteString.copyFromUtf8(foo);
        }

        @Override
        protected GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            return new GeneratedMessageTestSupport.Builder();
        }

        @Override
        public GeneratedMessageTestSupport.Builder newBuilderForType() {
            return newBuilder();
        }

        @Override
        public GeneratedMessageTestSupport.Builder toBuilder() {
            return newBuilder().setFoo(foo);
        }

        @Override
        public ReflectiveMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Parser<ReflectiveMessage> getParserForType() {
            return PARSER;
        }
    }

    public static final class Builder extends GeneratedMessage.Builder<Builder> {
        private String foo = "";

        private Builder() {
        }

        public boolean hasFoo() {
            return !foo.isEmpty();
        }

        public String getFoo() {
            return foo;
        }

        public ByteString getFooBytes() {
            return ByteString.copyFromUtf8(foo);
        }

        public Builder setFoo(String value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            foo = value;
            onChanged();
            return this;
        }

        public Builder clearFoo() {
            foo = "";
            onChanged();
            return this;
        }

        public Builder setFooBytes(ByteString value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            foo = value.toStringUtf8();
            onChanged();
            return this;
        }

        @Override
        protected GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Builder clone() {
            Builder builder = new Builder();
            builder.foo = foo;
            return builder;
        }

        @Override
        public ReflectiveMessage build() {
            ReflectiveMessage result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        @Override
        public ReflectiveMessage buildPartial() {
            ReflectiveMessage result = new ReflectiveMessage(foo);
            onBuilt();
            return result;
        }

        @Override
        public Builder mergeFrom(Message other) {
            if (other instanceof ReflectiveMessage) {
                ReflectiveMessage message = (ReflectiveMessage) other;
                if (message.hasFoo()) {
                    setFoo(message.getFoo());
                }
                mergeUnknownFields(message.getUnknownFields());
                return this;
            }
            super.mergeFrom(other);
            return this;
        }

        @Override
        public ReflectiveMessage getDefaultInstanceForType() {
            return ReflectiveMessage.DEFAULT_INSTANCE;
        }
    }
}
