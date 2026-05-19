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
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.Message;

public final class DescriptorMessageInfoFactoryCoverageMessage extends GeneratedMessageV3 {
    private static final String NO_BUILDERS_MESSAGE =
            "Builders are not needed for this coverage fixture";
    private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = buildFileDescriptor();
    private static final Descriptors.Descriptor MESSAGE_DESCRIPTOR =
            FILE_DESCRIPTOR.findMessageTypeByName("CoverageMessage");
    private static final Descriptors.Descriptor NESTED_DESCRIPTOR =
            FILE_DESCRIPTOR.findMessageTypeByName("CoverageNested");
    private static final GeneratedMessageV3.FieldAccessorTable FIELD_ACCESSOR_TABLE =
            new GeneratedMessageV3.FieldAccessorTable(
                    MESSAGE_DESCRIPTOR,
                    new String[] {"Name", "Children", "Chosen", "Number"});
    private static final GeneratedMessageV3.FieldAccessorTable NESTED_FIELD_ACCESSOR_TABLE =
            new GeneratedMessageV3.FieldAccessorTable(NESTED_DESCRIPTOR, new String[] {"Value"});
    private static final DescriptorMessageInfoFactoryCoverageMessage DEFAULT_INSTANCE =
            new DescriptorMessageInfoFactoryCoverageMessage();

    private String name_ = "";
    private List<DescriptorMessageInfoFactoryCoverageNested> children_ = Collections.emptyList();
    private int choiceCase_ = 0;
    private Object choice_;

    public static DescriptorMessageInfoFactoryCoverageMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    static GeneratedMessageV3.FieldAccessorTable nestedFieldAccessorTable() {
        return NESTED_FIELD_ACCESSOR_TABLE;
    }

    public String getName() {
        return name_;
    }

    public List<DescriptorMessageInfoFactoryCoverageNested> getChildrenList() {
        return children_;
    }

    public int getChildrenCount() {
        return children_.size();
    }

    public DescriptorMessageInfoFactoryCoverageNested getChildren(int index) {
        return children_.get(index);
    }

    public DescriptorMessageInfoFactoryCoverageNested getChosen() {
        if (choiceCase_ == 3) {
            return (DescriptorMessageInfoFactoryCoverageNested) choice_;
        }
        return DescriptorMessageInfoFactoryCoverageNested.getDefaultInstance();
    }

    public int getNumber() {
        if (choiceCase_ == 4) {
            return (Integer) choice_;
        }
        return 0;
    }

    public int getChoiceCaseValue() {
        return choiceCase_;
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
        DescriptorProtos.DescriptorProto nested = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("CoverageNested")
                .addField(field("value", 1, Label.LABEL_OPTIONAL, Type.TYPE_STRING))
                .build();

        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("CoverageMessage")
                .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName("choice"))
                .addField(field("name", 1, Label.LABEL_OPTIONAL, Type.TYPE_STRING))
                .addField(field("children", 2, Label.LABEL_REPEATED, Type.TYPE_MESSAGE)
                        .setTypeName(".coverage.CoverageNested"))
                .addField(field("chosen", 3, Label.LABEL_OPTIONAL, Type.TYPE_MESSAGE)
                        .setTypeName(".coverage.CoverageNested")
                        .setOneofIndex(0))
                .addField(field("number", 4, Label.LABEL_OPTIONAL, Type.TYPE_INT32)
                        .setOneofIndex(0))
                .build();

        DescriptorProtos.FileDescriptorProto file =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("descriptor_message_info_factory_coverage.proto")
                .setPackage("coverage")
                .setSyntax("proto3")
                .addMessageType(nested)
                .addMessageType(message)
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder field(
            String name,
            int number,
            Label label,
            Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(label)
                .setType(type);
    }
}
