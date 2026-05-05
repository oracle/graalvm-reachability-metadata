/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13;

import akka.protobufv3.internal.CodedInputStream;
import akka.protobufv3.internal.DescriptorProtos;
import akka.protobufv3.internal.Descriptors;
import akka.protobufv3.internal.ExtensionRegistryLite;
import akka.protobufv3.internal.GeneratedMessageV3;
import akka.protobufv3.internal.InvalidProtocolBufferException;
import akka.protobufv3.internal.Message;

/**
 * Minimal proto2 full-runtime message used to drive schema creation through public/protected APIs.
 */
public final class Proto2FullRuntimeMessage extends GeneratedMessageV3 {
    private static final Descriptors.Descriptor DESCRIPTOR;
    private static final FieldAccessorTable ACCESSOR_TABLE;
    private static final Proto2FullRuntimeMessage DEFAULT_INSTANCE = new Proto2FullRuntimeMessage();

    static {
        DescriptorProtos.DescriptorProto messageType = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Proto2FullRuntimeMessage")
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("extension_schemas_probe.proto")
                .addMessageType(messageType)
                .build();
        try {
            Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                    file,
                    new Descriptors.FileDescriptor[0]);
            DESCRIPTOR = fileDescriptor.findMessageTypeByName("Proto2FullRuntimeMessage");
            ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Proto2FullRuntimeMessage() {
    }

    public static Proto2FullRuntimeMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public void mergeEmptyPayloadWithFullRuntimeSchema() throws InvalidProtocolBufferException {
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);
        mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return ACCESSOR_TABLE;
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return DESCRIPTOR;
    }

    @Override
    public Proto2FullRuntimeMessage getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Message.Builder newBuilderForType() {
        throw new UnsupportedOperationException("Builder is not needed by this schema probe.");
    }

    @Override
    public Message.Builder toBuilder() {
        throw new UnsupportedOperationException("Builder is not needed by this schema probe.");
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        throw new UnsupportedOperationException("Builder is not needed by this schema probe.");
    }
}
