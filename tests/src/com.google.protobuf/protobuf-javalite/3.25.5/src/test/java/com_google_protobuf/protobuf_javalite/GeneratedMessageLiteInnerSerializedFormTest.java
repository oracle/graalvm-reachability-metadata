/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteInnerSerializedFormTest {
    private static final int STREAM_MAGIC = 0xaced;
    private static final int STREAM_VERSION = 5;
    private static final String SERIALIZED_FORM_CLASS_NAME =
            "com.google.protobuf.GeneratedMessageLite$SerializedForm";
    private static final String BYTE_ARRAY_CLASS_NAME = "[B";
    private static final String STRING_CLASS_NAME = "Ljava/lang/String;";
    private static final long BYTE_ARRAY_SERIAL_VERSION_UID = -5984413125824719648L;

    @Test
    public void javaSerializationRestoresMessageThroughDefaultInstanceField() throws Exception {
        StandardSerializedFormMessage original = StandardSerializedFormMessage.defaultInstance();

        Object restored = deserialize(serialize(original));

        StandardSerializedFormMessage message =
                assertInstanceOf(StandardSerializedFormMessage.class, restored);
        assertEquals(original.toByteArray().length, message.toByteArray().length);
    }

    @Test
    public void legacySerializedFormRestoresMessageThroughLowercaseDefaultInstanceField()
            throws Exception {
        Object restored = deserialize(legacySerializedFormBytes(LegacySerializedFormMessage.class));

        LegacySerializedFormMessage message = assertInstanceOf(LegacySerializedFormMessage.class, restored);
        assertEquals(0, message.toByteArray().length);
    }

    private static byte[] serialize(MessageLite message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(message);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    /**
     * Builds a stream with the pre-3.6.1 serialized fields so the absent `messageClass` field is
     * restored as `null` and the serialized form resolves the message class by name.
     */
    private static byte[] legacySerializedFormBytes(Class<? extends MessageLite> messageClass)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(STREAM_MAGIC);
        output.writeShort(STREAM_VERSION);
        output.writeByte(0x73); // TC_OBJECT
        output.writeByte(0x72); // TC_CLASSDESC
        output.writeUTF(SERIALIZED_FORM_CLASS_NAME);
        output.writeLong(0L);
        output.writeByte(0x02); // SC_SERIALIZABLE
        output.writeShort(2);
        writeObjectField(output, '[', "asBytes", BYTE_ARRAY_CLASS_NAME);
        writeObjectField(output, 'L', "messageClassName", STRING_CLASS_NAME);
        output.writeByte(0x78); // TC_ENDBLOCKDATA
        output.writeByte(0x70); // TC_NULL superclass
        writeEmptyByteArray(output);
        output.writeByte(0x74); // TC_STRING
        output.writeUTF(messageClass.getName());
        return bytes.toByteArray();
    }

    private static void writeObjectField(
            DataOutputStream output, char typeCode, String fieldName, String className)
            throws IOException {
        output.writeByte(typeCode);
        output.writeUTF(fieldName);
        output.writeByte(0x74); // TC_STRING
        output.writeUTF(className);
    }

    private static void writeEmptyByteArray(DataOutputStream output) throws IOException {
        output.writeByte(0x75); // TC_ARRAY
        output.writeByte(0x72); // TC_CLASSDESC
        output.writeUTF(BYTE_ARRAY_CLASS_NAME);
        output.writeLong(BYTE_ARRAY_SERIAL_VERSION_UID);
        output.writeByte(0x02); // SC_SERIALIZABLE
        output.writeShort(0);
        output.writeByte(0x78); // TC_ENDBLOCKDATA
        output.writeByte(0x70); // TC_NULL superclass
        output.writeInt(0);
    }

    private static final class StandardSerializedFormMessage
            extends GeneratedMessageLite<StandardSerializedFormMessage, StandardSerializedFormMessageBuilder>
            implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final StandardSerializedFormMessage DEFAULT_INSTANCE =
                new StandardSerializedFormMessage();
        private static volatile Parser<StandardSerializedFormMessage> parser;

        static {
            registerDefaultInstance(StandardSerializedFormMessage.class, DEFAULT_INSTANCE);
        }

        private StandardSerializedFormMessage() {
        }

        static StandardSerializedFormMessage defaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @SuppressWarnings("unused")
        private Object writeReplace() {
            return SerializedForm.of(this);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new StandardSerializedFormMessage();
                case NEW_BUILDER:
                    return new StandardSerializedFormMessageBuilder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<StandardSerializedFormMessage> result = parser;
                    if (result == null) {
                        synchronized (StandardSerializedFormMessage.class) {
                            result = parser;
                            if (result == null) {
                                result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = result;
                            }
                        }
                    }
                    return result;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    private static final class StandardSerializedFormMessageBuilder
            extends GeneratedMessageLite.Builder<
                    StandardSerializedFormMessage, StandardSerializedFormMessageBuilder> {
        private StandardSerializedFormMessageBuilder() {
            super(StandardSerializedFormMessage.defaultInstance());
        }
    }

    private static final class LegacySerializedFormMessage
            extends GeneratedMessageLite<LegacySerializedFormMessage, LegacySerializedFormMessageBuilder>
            implements Serializable {
        private static final long serialVersionUID = 1L;
        private static LegacySerializedFormMessage defaultInstance = new LegacySerializedFormMessage();
        private static volatile Parser<LegacySerializedFormMessage> parser;

        static {
            registerDefaultInstance(LegacySerializedFormMessage.class, defaultInstance);
        }

        private LegacySerializedFormMessage() {
        }

        @SuppressWarnings("unused")
        private Object writeReplace() {
            return SerializedForm.of(this);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacySerializedFormMessage();
                case NEW_BUILDER:
                    return new LegacySerializedFormMessageBuilder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_PARSER:
                    Parser<LegacySerializedFormMessage> result = parser;
                    if (result == null) {
                        synchronized (LegacySerializedFormMessage.class) {
                            result = parser;
                            if (result == null) {
                                result = new DefaultInstanceBasedParser<>(defaultInstance);
                                parser = result;
                            }
                        }
                    }
                    return result;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    private static final class LegacySerializedFormMessageBuilder
            extends GeneratedMessageLite.Builder<LegacySerializedFormMessage, LegacySerializedFormMessageBuilder> {
        private LegacySerializedFormMessageBuilder() {
            super(LegacySerializedFormMessage.defaultInstance);
        }
    }
}
