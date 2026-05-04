/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import java.util.Collections;
import java.util.List;

import org.apache.pekko.protobufv3.internal.CodedInputStream;
import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.Message;

public final class DescriptorMessageInfoFactoryProbe extends GeneratedMessageV3 {
    private static final DescriptorMessageInfoFactoryProbe DEFAULT_INSTANCE = new DescriptorMessageInfoFactoryProbe();
    private static final Descriptors.Descriptor DESCRIPTOR = buildDescriptor();

    private int choiceCase_ = 0;
    private Object choice_;
    private List<DescriptorMessageInfoFactoryProbeChild> repeatedChild_ = Collections.emptyList();
    private String regularText_ = "";

    public static DescriptorMessageInfoFactoryProbe getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public void initializeEmptyPayloadSchema() throws InvalidProtocolBufferException {
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);
        mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
    }

    public DescriptorMessageInfoFactoryProbeChild getMessageChoice() {
        if (choiceCase_ == 1) {
            return (DescriptorMessageInfoFactoryProbeChild) choice_;
        }
        return DescriptorMessageInfoFactoryProbeChild.getDefaultInstance();
    }

    public DescriptorMessageInfoFactoryProbeChild getRepeatedChild(int index) {
        return repeatedChild_.get(index);
    }

    public List<DescriptorMessageInfoFactoryProbeChild> getRepeatedChildList() {
        return repeatedChild_;
    }

    public String getRegularText() {
        return regularText_;
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return DESCRIPTOR;
    }

    @Override
    public DescriptorMessageInfoFactoryProbe getDefaultInstanceForType() {
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
        DescriptorProtos.DescriptorProto child = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Child")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("name")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();

        DescriptorProtos.DescriptorProto probe = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Probe")
                .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName("choice"))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("message_choice")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".coverage.Child")
                        .setOneofIndex(0))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("repeated_child")
                        .setNumber(2)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".coverage.Child"))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("regular_text")
                        .setNumber(3)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("descriptor_message_info_factory_probe.proto")
                .setPackage("coverage")
                .setSyntax("proto3")
                .addMessageType(child)
                .addMessageType(probe)
                .build();
        try {
            Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(
                    file,
                    new Descriptors.FileDescriptor[0]);
            return descriptor.findMessageTypeByName("Probe");
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}

final class DescriptorMessageInfoFactoryProbeChild extends GeneratedMessageV3 {
    private static final DescriptorMessageInfoFactoryProbeChild DEFAULT_INSTANCE =
            new DescriptorMessageInfoFactoryProbeChild();

    public static DescriptorMessageInfoFactoryProbeChild getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return DescriptorMessageInfoFactoryProbe.getDefaultInstance()
                .getDescriptorForType()
                .getFile()
                .findMessageTypeByName("Child");
    }

    @Override
    public DescriptorMessageInfoFactoryProbeChild getDefaultInstanceForType() {
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
}
