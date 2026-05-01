/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.protobuf.DescriptorProtos;
import org.apache.hadoop.thirdparty.protobuf.Descriptors;
import org.apache.hadoop.thirdparty.protobuf.GeneratedMessage;
import org.apache.hadoop.thirdparty.protobuf.Message;
import org.junit.jupiter.api.Test;

public class GeneratedMessageTest {
    private static final String FILE_NAME = "coverage/generated_message.proto";
    private static final String PACKAGE_NAME = "coverage.generatedmessage";
    private static final String MESSAGE_TYPE_NAME = "TrackedMessage";
    private static final String FIELD_NAME = "count";
    private static final int FIELD_NUMBER = 1;
    private static final int COUNT_VALUE = 42;

    @Test
    void generatedMessageFieldAccessorsUseGeneratedStyleMethods() {
        Descriptors.FieldDescriptor countField = TrackedMessage.getDescriptor().findFieldByName(FIELD_NAME);
        TrackedMessage message = new TrackedMessage(COUNT_VALUE);

        assertThat(message.hasField(countField)).isTrue();
        assertThat(message.getField(countField)).isEqualTo(COUNT_VALUE);
        assertThat(message.getAllFields()).containsEntry(countField, COUNT_VALUE);
        assertThat(message.getDescriptorForType()).isSameAs(TrackedMessage.getDescriptor());
    }

    private static Descriptors.FileDescriptor buildFileDescriptor() {
        DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(FILE_NAME)
                .setPackage(PACKAGE_NAME)
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName(MESSAGE_TYPE_NAME)
                        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                .setName(FIELD_NAME)
                                .setNumber(FIELD_NUMBER)
                                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)))
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new IllegalStateException("Test descriptor should be valid", exception);
        }
    }

    public static final class TrackedMessage extends GeneratedMessage {
        private static final TrackedMessage DEFAULT_INSTANCE = new TrackedMessage(0, false);
        private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = buildFileDescriptor();
        private static final Descriptors.Descriptor DESCRIPTOR = FILE_DESCRIPTOR.findMessageTypeByName(
                MESSAGE_TYPE_NAME
        );
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                DESCRIPTOR,
                new String[] {"Count"},
                TrackedMessage.class,
                TrackedMessageBuilder.class
        );

        private final int count;
        private final boolean hasCount;

        private TrackedMessage(int count) {
            this(count, true);
        }

        private TrackedMessage(int count, boolean hasCount) {
            this.count = count;
            this.hasCount = hasCount;
        }

        public static Descriptors.Descriptor getDescriptor() {
            return DESCRIPTOR;
        }

        public boolean hasCount() {
            return hasCount;
        }

        public int getCount() {
            return count;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessage.BuilderParent parent) {
            throw new UnsupportedOperationException("Builder creation is not needed for this coverage test");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder creation is not needed for this coverage test");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder creation is not needed for this coverage test");
        }

        @Override
        public TrackedMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }
    }

    public abstract static class TrackedMessageBuilder extends GeneratedMessage.Builder<TrackedMessageBuilder> {
        public abstract boolean hasCount();

        public abstract int getCount();

        public abstract TrackedMessageBuilder setCount(int count);

        public abstract TrackedMessageBuilder clearCount();
    }
}
