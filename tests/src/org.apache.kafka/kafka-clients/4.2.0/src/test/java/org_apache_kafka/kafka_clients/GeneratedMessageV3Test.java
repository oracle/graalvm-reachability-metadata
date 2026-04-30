/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

public class GeneratedMessageV3Test {
    @Test
    void generatedMessageV3FieldAccessorTableInvokesGeneratedAccessors() {
        ProbeMessage message = ProbeMessage.newBuilder().setProbeNumber(42).build();
        Descriptors.FieldDescriptor numberField = ProbeMessage.getDescriptor().findFieldByName("probe_number");

        assertTrue(message.hasField(numberField));
        assertEquals(42, message.getField(numberField));
        assertEquals(42, message.getAllFields().get(numberField));

        ProbeMessageBuilder builder = message.toBuilder();
        assertTrue(builder.hasField(numberField));
        assertEquals(42, builder.getField(numberField));

        builder.clearField(numberField);
        assertFalse(builder.hasField(numberField));

        builder.setField(numberField, 7);
        assertEquals(7, builder.build().getProbeNumber());
    }

    public static final class ProbeMessage extends GeneratedMessageV3 {
        private static final Descriptors.Descriptor DESCRIPTOR = TestProtoDescriptors.messageDescriptor();
        private static final ProbeMessage DEFAULT_INSTANCE = new ProbeMessage(false, 0);
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                DESCRIPTOR,
                new String[] {"ProbeNumber"},
                ProbeMessage.class,
                ProbeMessageBuilder.class);

        private final boolean hasProbeNumber;
        private final int probeNumber;

        private ProbeMessage(boolean hasProbeNumber, int probeNumber) {
            this.hasProbeNumber = hasProbeNumber;
            this.probeNumber = probeNumber;
            this.unknownFields = UnknownFieldSet.getDefaultInstance();
        }

        public static Descriptors.Descriptor getDescriptor() {
            return DESCRIPTOR;
        }

        public static ProbeMessageBuilder newBuilder() {
            return new ProbeMessageBuilder();
        }

        public boolean hasProbeNumber() {
            return hasProbeNumber;
        }

        public int getProbeNumber() {
            return probeNumber;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public ProbeMessageBuilder newBuilderForType() {
            return new ProbeMessageBuilder();
        }

        @Override
        public ProbeMessageBuilder toBuilder() {
            return new ProbeMessageBuilder().mergeFrom(this);
        }

        @Override
        protected ProbeMessageBuilder newBuilderForType(BuilderParent parent) {
            return new ProbeMessageBuilder();
        }

        @Override
        protected Object newInstance(UnusedPrivateParameter unused) {
            return new ProbeMessage(false, 0);
        }

        @Override
        public ProbeMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }
    }

    public static final class ProbeMessageBuilder extends GeneratedMessageV3.Builder<ProbeMessageBuilder> {
        private boolean hasProbeNumber;
        private int probeNumber;

        private ProbeMessageBuilder() {
        }

        public boolean hasProbeNumber() {
            return hasProbeNumber;
        }

        public int getProbeNumber() {
            return probeNumber;
        }

        public ProbeMessageBuilder setProbeNumber(int probeNumber) {
            this.hasProbeNumber = true;
            this.probeNumber = probeNumber;
            onChanged();
            return this;
        }

        public ProbeMessageBuilder clearProbeNumber() {
            hasProbeNumber = false;
            probeNumber = 0;
            onChanged();
            return this;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return ProbeMessage.FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return ProbeMessage.DESCRIPTOR;
        }

        @Override
        public ProbeMessageBuilder clear() {
            super.clear();
            hasProbeNumber = false;
            probeNumber = 0;
            return this;
        }

        @Override
        public ProbeMessageBuilder clone() {
            return new ProbeMessageBuilder().mergeFrom(buildPartial());
        }

        public ProbeMessageBuilder mergeFrom(ProbeMessage message) {
            if (message.hasProbeNumber()) {
                setProbeNumber(message.getProbeNumber());
            }
            mergeUnknownFields(message.getUnknownFields());
            return this;
        }

        @Override
        public ProbeMessage build() {
            return buildPartial();
        }

        @Override
        public ProbeMessage buildPartial() {
            ProbeMessage message = new ProbeMessage(hasProbeNumber, probeNumber);
            onBuilt();
            return message;
        }

        @Override
        public ProbeMessage getDefaultInstanceForType() {
            return ProbeMessage.DEFAULT_INSTANCE;
        }
    }

    private static final class TestProtoDescriptors {
        private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = fileDescriptor();

        private static Descriptors.Descriptor messageDescriptor() {
            return FILE_DESCRIPTOR.findMessageTypeByName("ProbeMessage");
        }

        private static Descriptors.FileDescriptor fileDescriptor() {
            DescriptorProtos.FieldDescriptorProto probeNumberField = DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName("probe_number")
                    .setNumber(1)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build();
            DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("ProbeMessage")
                    .addField(probeNumberField)
                    .build();
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("generated_message_v3_probe.proto")
                    .setPackage("generated_message_v3_probe")
                    .addMessageType(message)
                    .build();
            try {
                return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, new Descriptors.FileDescriptor[0]);
            } catch (Descriptors.DescriptorValidationException exception) {
                throw new IllegalStateException("Unable to build test descriptor", exception);
            }
        }
    }
}
