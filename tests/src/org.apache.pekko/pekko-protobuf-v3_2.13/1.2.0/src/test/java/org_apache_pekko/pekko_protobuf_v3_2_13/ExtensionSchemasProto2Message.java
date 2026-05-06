/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.CodedInputStream;
import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.Message;

public final class ExtensionSchemasProto2Message extends GeneratedMessageV3 {
    private static final String NO_BUILDERS_MESSAGE =
            "Builders are not needed for this coverage fixture";
    private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = buildFileDescriptor();
    private static final Descriptors.Descriptor MESSAGE_DESCRIPTOR =
            FILE_DESCRIPTOR.findMessageTypeByName("ExtensionSchemasProto2Message");
    private static final GeneratedMessageV3.FieldAccessorTable FIELD_ACCESSOR_TABLE =
            new GeneratedMessageV3.FieldAccessorTable(MESSAGE_DESCRIPTOR, new String[0]);
    private static final ExtensionSchemasProto2Message DEFAULT_INSTANCE =
            new ExtensionSchemasProto2Message();

    public static ExtensionSchemasProto2Message getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public void parseEmptyInputThroughSchema() throws InvalidProtocolBufferException {
        mergeFromAndMakeImmutableInternal(
                CodedInputStream.newInstance(new byte[0]),
                ExtensionRegistryLite.getEmptyRegistry());
    }

    @Override
    protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
        return FIELD_ACCESSOR_TABLE;
    }

    @Override
    protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message.Builder newBuilderForType() {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message.Builder toBuilder() {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    private static Descriptors.FileDescriptor buildFileDescriptor() {
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ExtensionSchemasProto2Message")
                .build();

        DescriptorProtos.FileDescriptorProto file =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("extension_schemas_proto2_coverage.proto")
                .setPackage("coverage")
                .setSyntax("proto2")
                .addMessageType(message)
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
