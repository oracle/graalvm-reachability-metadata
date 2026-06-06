/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.DynamicMessage;
import org.apache.pekko.protobufv3.internal.ExtensionRegistry;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.Message;

public final class ExtensionSchemasProto2Probe extends GeneratedMessageV3 {
    private static final ExtensionSchemasProto2Probe DEFAULT_INSTANCE = new ExtensionSchemasProto2Probe();
    private static final Descriptors.Descriptor DESCRIPTOR = buildDescriptor();

    public static ExtensionSchemasProto2Probe getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public void initializeEmptyPayloadSchema() throws InvalidProtocolBufferException {
        DynamicMessage parsed = DynamicMessage.parseFrom(
                getDescriptorForType(),
                new byte[0],
                ExtensionRegistry.getEmptyRegistry());
        if (!parsed.getAllFields().isEmpty()) {
            throw new InvalidProtocolBufferException("Empty input produced fields");
        }
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return DESCRIPTOR;
    }

    @Override
    public ExtensionSchemasProto2Probe getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Message.Builder newBuilderForType() {
        throw new UnsupportedOperationException("Builder is not needed for schema discovery coverage");
    }

    @Override
    public Message.Builder toBuilder() {
        throw new UnsupportedOperationException("Builder is not needed for schema discovery coverage");
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        throw new UnsupportedOperationException("FieldAccessorTable is not needed for schema discovery coverage");
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        throw new UnsupportedOperationException("Builder is not needed for schema discovery coverage");
    }

    private static Descriptors.Descriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto probe = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ExtensionSchemasProto2Probe")
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("extension_schemas_proto2_probe.proto")
                .setPackage("coverage")
                .addMessageType(probe)
                .build();
        try {
            Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(
                    file,
                    new Descriptors.FileDescriptor[0]);
            return descriptor.findMessageTypeByName("ExtensionSchemasProto2Probe");
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
