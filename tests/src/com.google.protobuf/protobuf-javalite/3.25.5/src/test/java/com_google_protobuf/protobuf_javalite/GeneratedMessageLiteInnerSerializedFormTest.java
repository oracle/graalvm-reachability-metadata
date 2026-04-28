/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.AbstractParser;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.StringValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageLiteInnerSerializedFormTest {
    private static final String SERIALIZED_FORM_CLASS_NAME =
            "com.google.protobuf.GeneratedMessageLite$SerializedForm";
    private static final byte TC_CLASS = 0x76;
    private static final byte TC_CLASSDESC = 0x72;
    private static final byte TC_ENDBLOCKDATA = 0x78;
    private static final byte TC_NULL = 0x70;
    private static final byte TC_OBJECT = 0x73;
    private static final byte TC_REFERENCE = 0x71;
    private static final byte TC_STRING = 0x74;

    @Test
    void deserializesCurrentFormThroughDefaultInstanceField() throws Exception {
        StringValue value = StringValue.of("serialized form coverage");

        Object serializedForm = LegacyDefaultInstanceMessage.serializedFormOf(value);
        Object restored = deserialize(serialize(serializedForm));

        assertThat(restored).isEqualTo(value);
    }

    @Test
    void deserializesOlderFormByResolvingMessageClassName() throws Exception {
        StringValue value = StringValue.of("serialized form class name coverage");
        byte[] currentForm = serialize(LegacyDefaultInstanceMessage.serializedFormOf(value));
        byte[] oldFormWithoutMessageClass = stripMessageClassField(currentForm);

        Object restored = deserialize(oldFormWithoutMessageClass);

        assertThat(restored).isEqualTo(value);
    }

    @Test
    void deserializesLegacyFormThroughLowercaseDefaultInstanceField() throws Exception {
        LegacyDefaultInstanceMessage message = LegacyDefaultInstanceMessage.newDefaultInstance();

        Object restored = deserialize(serialize(message.serializedForm()));

        assertThat(restored).isInstanceOf(LegacyDefaultInstanceMessage.class);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static byte[] stripMessageClassField(byte[] serializedForm) throws IOException {
        ClassDescriptor descriptor = readClassDescriptor(serializedForm);
        FieldDescriptor messageClassField = descriptor.fieldNamed("messageClass");
        int valuesStart = descriptor.valuesStart;
        int firstValueEnd = skipSerializedObject(serializedForm, valuesStart);
        int messageClassValueEnd = skipSerializedObject(serializedForm, firstValueEnd);

        ByteArrayOutputStream stripped = new ByteArrayOutputStream(serializedForm.length);
        stripped.write(serializedForm, 0, descriptor.fieldCountOffset);
        writeShort(stripped, descriptor.fields.length - 1);
        stripped.write(
                serializedForm,
                descriptor.fieldsStart,
                messageClassField.start - descriptor.fieldsStart);
        stripped.write(
                serializedForm,
                messageClassField.end,
                descriptor.fieldsEnd - messageClassField.end);
        stripped.write(serializedForm, descriptor.fieldsEnd, firstValueEnd - descriptor.fieldsEnd);
        stripped.write(
                serializedForm,
                messageClassValueEnd,
                serializedForm.length - messageClassValueEnd);
        return stripped.toByteArray();
    }

    private static ClassDescriptor readClassDescriptor(byte[] serializedForm) throws IOException {
        int offset = 0;
        offset = requireShort(serializedForm, offset, 0xACED);
        offset = requireShort(serializedForm, offset, 5);
        offset = requireByte(serializedForm, offset, TC_OBJECT);
        offset = requireByte(serializedForm, offset, TC_CLASSDESC);
        UtfValue className = readUtf(serializedForm, offset);
        assertThat(className.value).isEqualTo(SERIALIZED_FORM_CLASS_NAME);
        offset = className.end + Long.BYTES + 1;

        int fieldCountOffset = offset;
        int fieldCount = readUnsignedShort(serializedForm, offset);
        offset += Short.BYTES;
        int fieldsStart = offset;
        FieldDescriptor[] fields = new FieldDescriptor[fieldCount];
        for (int index = 0; index < fieldCount; index++) {
            fields[index] = readFieldDescriptor(serializedForm, offset);
            offset = fields[index].end;
        }
        int fieldsEnd = offset;
        offset = requireByte(serializedForm, offset, TC_ENDBLOCKDATA);
        offset = requireByte(serializedForm, offset, TC_NULL);
        return new ClassDescriptor(fieldCountOffset, fieldsStart, fieldsEnd, offset, fields);
    }

    private static FieldDescriptor readFieldDescriptor(byte[] data, int start) throws IOException {
        int offset = start + 1;
        UtfValue name = readUtf(data, offset);
        offset = name.end;
        byte type = data[start];
        if (type == 'L' || type == '[') {
            offset = skipSerializedObject(data, offset);
        }
        return new FieldDescriptor(name.value, start, offset);
    }

    private static int skipSerializedObject(byte[] data, int offset) throws IOException {
        byte type = data[offset];
        if (type == TC_NULL) {
            return offset + 1;
        }
        if (type == TC_REFERENCE) {
            return offset + 5;
        }
        if (type == TC_STRING) {
            return readUtf(data, offset + 1).end;
        }
        if (type == TC_CLASS) {
            return skipClassDescriptor(data, offset + 1);
        }
        if (type == TC_OBJECT) {
            throw new IOException("Unexpected object value in serialized form");
        }
        if (type == 0x75) {
            int afterClassDescriptor = skipClassDescriptor(data, offset + 1);
            int length = readInt(data, afterClassDescriptor);
            return afterClassDescriptor + Integer.BYTES + length;
        }
        throw new IOException("Unexpected serialized token: " + Integer.toHexString(type & 0xFF));
    }

    private static int skipClassDescriptor(byte[] data, int offset) throws IOException {
        byte type = data[offset];
        if (type == TC_NULL) {
            return offset + 1;
        }
        if (type == TC_REFERENCE) {
            return offset + 5;
        }
        if (type != TC_CLASSDESC) {
            throw new IOException(
                    "Unexpected class descriptor token: " + Integer.toHexString(type & 0xFF));
        }

        offset++;
        offset = readUtf(data, offset).end + Long.BYTES + 1;
        int fieldCount = readUnsignedShort(data, offset);
        offset += Short.BYTES;
        for (int index = 0; index < fieldCount; index++) {
            offset = readFieldDescriptor(data, offset).end;
        }
        offset = requireByte(data, offset, TC_ENDBLOCKDATA);
        return skipClassDescriptor(data, offset);
    }

    private static int requireByte(byte[] data, int offset, byte expected) throws IOException {
        if (data[offset] != expected) {
            throw new IOException(
                    "Expected token " + Integer.toHexString(expected & 0xFF)
                            + " but found " + Integer.toHexString(data[offset] & 0xFF));
        }
        return offset + 1;
    }

    private static int requireShort(byte[] data, int offset, int expected) throws IOException {
        int actual = readUnsignedShort(data, offset);
        if (actual != expected) {
            throw new IOException("Expected " + expected + " but found " + actual);
        }
        return offset + Short.BYTES;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static UtfValue readUtf(byte[] data, int offset) {
        int length = readUnsignedShort(data, offset);
        int start = offset + Short.BYTES;
        String value = new String(data, start, length, StandardCharsets.UTF_8);
        return new UtfValue(value, start + length);
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static final class ClassDescriptor {
        private final int fieldCountOffset;
        private final int fieldsStart;
        private final int fieldsEnd;
        private final int valuesStart;
        private final FieldDescriptor[] fields;

        private ClassDescriptor(
                int fieldCountOffset,
                int fieldsStart,
                int fieldsEnd,
                int valuesStart,
                FieldDescriptor[] fields) {
            this.fieldCountOffset = fieldCountOffset;
            this.fieldsStart = fieldsStart;
            this.fieldsEnd = fieldsEnd;
            this.valuesStart = valuesStart;
            this.fields = fields;
        }

        private FieldDescriptor fieldNamed(String name) throws IOException {
            for (FieldDescriptor field : fields) {
                if (field.name.equals(name)) {
                    return field;
                }
            }
            throw new IOException("Field not found in serialized form: " + name);
        }
    }

    private static final class FieldDescriptor {
        private final String name;
        private final int start;
        private final int end;

        private FieldDescriptor(String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    private static final class UtfValue {
        private final String value;
        private final int end;

        private UtfValue(String value, int end) {
            this.value = value;
            this.end = end;
        }
    }

    private static final class LegacyDefaultInstanceMessage
            extends GeneratedMessageLite<
                    LegacyDefaultInstanceMessage, LegacyDefaultInstanceMessage.Builder> {
        private static LegacyDefaultInstanceMessage defaultInstance =
                new LegacyDefaultInstanceMessage();

        static {
            defaultInstance.makeImmutable();
        }

        private static LegacyDefaultInstanceMessage newDefaultInstance() {
            return defaultInstance;
        }

        private static Object serializedFormOf(MessageLite message) {
            return SerializedForm.of(message);
        }

        private Object serializedForm() {
            return SerializedForm.of(this);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacyDefaultInstanceMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", new Object[0]);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_PARSER:
                    return new AbstractParser<LegacyDefaultInstanceMessage>() {
                        @Override
                        public LegacyDefaultInstanceMessage parsePartialFrom(
                                CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                                throws InvalidProtocolBufferException {
                            return new LegacyDefaultInstanceMessage();
                        }
                    };
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        private static final class Builder
                extends GeneratedMessageLite.Builder<LegacyDefaultInstanceMessage, Builder> {
            private Builder() {
                super(defaultInstance);
            }
        }
    }
}
