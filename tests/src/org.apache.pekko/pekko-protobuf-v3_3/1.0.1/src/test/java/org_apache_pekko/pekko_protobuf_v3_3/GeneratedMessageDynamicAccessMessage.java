/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto;
import org.apache.pekko.protobufv3.internal.Descriptors.Descriptor;
import org.apache.pekko.protobufv3.internal.Descriptors.DescriptorValidationException;
import org.apache.pekko.protobufv3.internal.Descriptors.FieldDescriptor;
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor;
import org.apache.pekko.protobufv3.internal.GeneratedMessage;
import org.apache.pekko.protobufv3.internal.Message;
import org.apache.pekko.protobufv3.internal.Parser;

public final class GeneratedMessageDynamicAccessMessage extends GeneratedMessage {
    private static final String FIELD_NAME = "Count";
    private static final GeneratedMessageDynamicAccessMessage DEFAULT_INSTANCE =
            new GeneratedMessageDynamicAccessMessage(0);

    public static final FileDescriptor descriptor = createDescriptor();
    public static final Descriptor messageDescriptor = descriptor.findMessageTypeByName("DynamicAccessMessage");
    public static final FieldDescriptor countField = messageDescriptor.findFieldByName("count");
    private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
            messageDescriptor,
            new String[] {FIELD_NAME },
            GeneratedMessageDynamicAccessMessage.class,
            Builder.class);

    private final int count;

    private GeneratedMessageDynamicAccessMessage(int count) {
        this.count = count;
    }

    public static GeneratedMessageDynamicAccessMessage of(int count) {
        return new GeneratedMessageDynamicAccessMessage(count);
    }

    public int getCount() {
        return count;
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return FIELD_ACCESSOR_TABLE;
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        return new Builder(parent);
    }

    @Override
    public Message.Builder newBuilderForType() {
        return new Builder();
    }

    @Override
    public Message.Builder toBuilder() {
        return new Builder().setCount(count);
    }

    @Override
    public Message getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Parser<? extends GeneratedMessage> getParserForType() {
        throw new UnsupportedOperationException("Parsing is not needed for GeneratedMessage dynamic-access coverage.");
    }

    private static FileDescriptor createDescriptor() {
        DescriptorProto message = DescriptorProto.newBuilder()
                .setName("DynamicAccessMessage")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("count")
                        .setNumber(1)
                        .setLabel(Label.LABEL_OPTIONAL)
                        .setType(Type.TYPE_INT32)
                        .build())
                .build();
        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("generated_message_dynamic_access.proto")
                .setPackage("dynamicaccess.generatedmessage")
                .setSyntax("proto3")
                .addMessageType(message)
                .build();
        try {
            return FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        } catch (DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final class Builder extends GeneratedMessage.Builder<Builder> {
        private int count;

        public Builder() {
        }

        private Builder(BuilderParent parent) {
            super(parent);
        }

        public int getCount() {
            return count;
        }

        public Builder setCount(int count) {
            this.count = count;
            onChanged();
            return this;
        }

        public Builder clearCount() {
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
            count = 0;
            return this;
        }

        @Override
        public GeneratedMessageDynamicAccessMessage build() {
            return buildPartial();
        }

        @Override
        public GeneratedMessageDynamicAccessMessage buildPartial() {
            GeneratedMessageDynamicAccessMessage result = new GeneratedMessageDynamicAccessMessage(count);
            onBuilt();
            return result;
        }

        @Override
        public Builder clone() {
            return new Builder().setCount(count);
        }

        @Override
        public Message getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Builder mergeFrom(Message other) {
            if (other instanceof GeneratedMessageDynamicAccessMessage message) {
                setCount(message.getCount());
                return this;
            }
            return super.mergeFrom(other);
        }
    }
}
