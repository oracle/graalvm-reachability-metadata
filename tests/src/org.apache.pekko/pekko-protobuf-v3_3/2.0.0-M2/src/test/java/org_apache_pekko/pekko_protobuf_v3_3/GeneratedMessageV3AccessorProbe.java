/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import java.io.IOException;

import org.apache.pekko.protobufv3.internal.CodedInputStream;
import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.Message;
import org.apache.pekko.protobufv3.internal.UnknownFieldSet;

public final class GeneratedMessageV3AccessorProbe extends GeneratedMessageV3 {
    private static final Descriptors.Descriptor DESCRIPTOR = buildDescriptor();
    private static final GeneratedMessageV3AccessorProbe DEFAULT_INSTANCE = new GeneratedMessageV3AccessorProbe(false, 0);
    private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
            DESCRIPTOR,
            new String[] {"Number"})
            .ensureFieldAccessorsInitialized(GeneratedMessageV3AccessorProbe.class, Builder.class);

    private final boolean hasNumber;
    private final int number;

    private GeneratedMessageV3AccessorProbe(boolean hasNumber, int number) {
        this.hasNumber = hasNumber;
        this.number = number;
        this.unknownFields = UnknownFieldSet.getDefaultInstance();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static GeneratedMessageV3AccessorProbe getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static Descriptors.Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public boolean hasNumber() {
        return hasNumber;
    }

    public int getNumber() {
        return number;
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return DESCRIPTOR;
    }

    @Override
    public GeneratedMessageV3AccessorProbe getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Builder newBuilderForType() {
        return new Builder();
    }

    @Override
    public Builder toBuilder() {
        return newBuilder().mergeFrom(this);
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return FIELD_ACCESSOR_TABLE;
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        return new Builder(parent);
    }

    private static Descriptors.Descriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto probe = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ProbeV3")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("number")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("generated_message_v3_accessor_probe.proto")
                .setPackage("coverage.generated_message_v3")
                .setSyntax("proto2")
                .addMessageType(probe)
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0])
                    .findMessageTypeByName("ProbeV3");
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final class Builder extends GeneratedMessageV3.Builder<Builder> {
        private boolean hasNumber;
        private int number;

        private Builder() {
        }

        private Builder(BuilderParent parent) {
            super(parent);
        }

        public boolean hasNumber() {
            return hasNumber;
        }

        public int getNumber() {
            return number;
        }

        public Builder setNumber(int value) {
            hasNumber = true;
            number = value;
            onChanged();
            return this;
        }

        public Builder clearNumber() {
            hasNumber = false;
            number = 0;
            onChanged();
            return this;
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        @Override
        public GeneratedMessageV3AccessorProbe getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Builder clear() {
            super.clear();
            hasNumber = false;
            number = 0;
            return this;
        }

        @Override
        public GeneratedMessageV3AccessorProbe build() {
            GeneratedMessageV3AccessorProbe result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        @Override
        public GeneratedMessageV3AccessorProbe buildPartial() {
            return new GeneratedMessageV3AccessorProbe(hasNumber, number);
        }

        @Override
        public Builder clone() {
            return newBuilder().mergeFrom(buildPartial());
        }

        @Override
        public Builder mergeFrom(Message other) {
            if (other instanceof GeneratedMessageV3AccessorProbe) {
                return mergeFrom((GeneratedMessageV3AccessorProbe) other);
            }
            super.mergeFrom(other);
            return this;
        }

        public Builder mergeFrom(GeneratedMessageV3AccessorProbe other) {
            if (other.hasNumber()) {
                setNumber(other.getNumber());
            }
            mergeUnknownFields(other.getUnknownFields());
            return this;
        }

        @Override
        public Builder mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            throw new UnsupportedOperationException("Parsing is not needed for GeneratedMessageV3 reflection coverage");
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }
    }
}
