/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.Message;

public final class GeneratedMessageV3Probe extends GeneratedMessageV3 {
    private static final long serialVersionUID = 1L;

    private static final GeneratedMessageV3Probe DEFAULT_INSTANCE = new GeneratedMessageV3Probe(false, 0);
    private static final Descriptors.Descriptor DESCRIPTOR = buildDescriptor();
    private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
            DESCRIPTOR,
            new String[] {"Quantity"});

    private final boolean hasQuantity;
    private final int quantity;

    private GeneratedMessageV3Probe(boolean hasQuantity, int quantity) {
        this.hasQuantity = hasQuantity;
        this.quantity = quantity;
    }

    private GeneratedMessageV3Probe(Builder builder) {
        super(builder);
        this.hasQuantity = builder.hasQuantity;
        this.quantity = builder.quantity;
    }

    public static GeneratedMessageV3Probe getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public boolean hasQuantity() {
        return hasQuantity;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public GeneratedMessageV3Probe getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Builder newBuilderForType() {
        return newBuilder();
    }

    @Override
    public Builder toBuilder() {
        return newBuilder().mergeFrom(this);
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return FIELD_ACCESSOR_TABLE.ensureFieldAccessorsInitialized(GeneratedMessageV3Probe.class, Builder.class);
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        return new Builder(parent);
    }

    public static final class Builder extends GeneratedMessageV3.Builder<Builder> {
        private boolean hasQuantity;
        private int quantity;

        private Builder() {
        }

        private Builder(BuilderParent parent) {
            super(parent);
        }

        public boolean hasQuantity() {
            return hasQuantity;
        }

        public int getQuantity() {
            return quantity;
        }

        public Builder setQuantity(int value) {
            hasQuantity = true;
            quantity = value;
            onChanged();
            return this;
        }

        public Builder clearQuantity() {
            hasQuantity = false;
            quantity = 0;
            onChanged();
            return this;
        }

        public Builder mergeFrom(GeneratedMessageV3Probe other) {
            if (other.hasQuantity()) {
                setQuantity(other.getQuantity());
            }
            mergeUnknownFields(other.getUnknownFields());
            return this;
        }

        @Override
        public Builder clear() {
            super.clear();
            hasQuantity = false;
            quantity = 0;
            return this;
        }

        @Override
        public GeneratedMessageV3Probe build() {
            return buildPartial();
        }

        @Override
        public GeneratedMessageV3Probe buildPartial() {
            onBuilt();
            return new GeneratedMessageV3Probe(this);
        }

        @Override
        public GeneratedMessageV3Probe getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE.ensureFieldAccessorsInitialized(GeneratedMessageV3Probe.class, Builder.class);
        }
    }

    private static Descriptors.Descriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("GeneratedMessageV3Probe")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("quantity")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("generated_message_v3_probe.proto")
                .setPackage("coverage")
                .setSyntax("proto2")
                .addMessageType(message)
                .build();
        try {
            Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(
                    file,
                    new Descriptors.FileDescriptor[0]);
            return descriptor.findMessageTypeByName("GeneratedMessageV3Probe");
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
