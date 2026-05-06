/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13;

import java.io.IOException;

import akka.protobufv3.internal.AbstractParser;
import akka.protobufv3.internal.CodedInputStream;
import akka.protobufv3.internal.Descriptors;
import akka.protobufv3.internal.ExtensionRegistryLite;
import akka.protobufv3.internal.GeneratedMessageV3;
import akka.protobufv3.internal.MapEntry;
import akka.protobufv3.internal.MapField;
import akka.protobufv3.internal.Message;
import akka.protobufv3.internal.Parser;
import akka.protobufv3.internal.Struct;
import akka.protobufv3.internal.UnknownFieldSet;
import akka.protobufv3.internal.Value;
import akka.protobufv3.internal.WireFormat;

public final class StructMapFieldProbeMessage extends GeneratedMessageV3 {
    private static final StructMapFieldProbeMessage DEFAULT_INSTANCE = new StructMapFieldProbeMessage();
    private static final Parser<StructMapFieldProbeMessage> PARSER = new AbstractParser<StructMapFieldProbeMessage>() {
        @Override
        public StructMapFieldProbeMessage parsePartialFrom(
                CodedInputStream input,
                ExtensionRegistryLite extensionRegistry
        ) {
            return new StructMapFieldProbeMessage();
        }
    };

    private MapField<String, Value> fields_ = MapField.emptyMapField(FieldsDefaultEntryHolder.defaultEntry);

    public void mergeEmptyInputThroughRuntimeSchema() throws IOException {
        mergeFromAndMakeImmutableInternal(
                CodedInputStream.newInstance(new byte[0]),
                ExtensionRegistryLite.getEmptyRegistry()
        );
    }

    public int getFieldsCount() {
        return fields_.getMap().size();
    }

    public static StructMapFieldProbeMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return Struct.getDescriptor();
    }

    @Override
    public StructMapFieldProbeMessage getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Parser<StructMapFieldProbeMessage> getParserForType() {
        return PARSER;
    }

    @Override
    public UnknownFieldSet getUnknownFields() {
        return UnknownFieldSet.getDefaultInstance();
    }

    @Override
    public Message.Builder newBuilderForType() {
        return unsupportedBuilder();
    }

    @Override
    public Message.Builder toBuilder() {
        return unsupportedBuilder();
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        return unsupportedBuilder();
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        throw new UnsupportedOperationException("The schema probe is not built via generated accessors");
    }

    @Override
    protected MapField internalGetMapField(int number) {
        if (number == Struct.FIELDS_FIELD_NUMBER) {
            return fields_;
        }
        throw new IllegalArgumentException("Unsupported map field number: " + number);
    }

    private Message.Builder unsupportedBuilder() {
        throw new UnsupportedOperationException("The schema probe is not built via protobuf builders");
    }

    public static final class FieldsDefaultEntryHolder {
        public static final MapEntry<String, Value> defaultEntry = MapEntry.newDefaultInstance(
                Struct.getDescriptor().findFieldByNumber(Struct.FIELDS_FIELD_NUMBER).getMessageType(),
                WireFormat.FieldType.STRING,
                "",
                WireFormat.FieldType.MESSAGE,
                Value.getDefaultInstance()
        );

        private FieldsDefaultEntryHolder() {
        }
    }
}
