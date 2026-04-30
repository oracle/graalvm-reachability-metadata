/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.MessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteInnerSerializedFormTest {
    private static final int STREAM_MAGIC = 0xACED;
    private static final int STREAM_VERSION = 5;
    private static final int STREAM_HEADER_LENGTH = 4;
    private static final int TC_NULL = 0x70;
    private static final int TC_CLASSDESC = 0x72;
    private static final int TC_OBJECT = 0x73;
    private static final int TC_STRING = 0x74;
    private static final int TC_ENDBLOCKDATA = 0x78;
    private static final int SC_SERIALIZABLE = 0x02;
    private static final String SERIALIZED_FORM_CLASS_NAME =
            "org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite$SerializedForm";

    @Test
    void resolvesSerializedFormUsingDefaultInstanceFieldAndClassName() throws Exception {
        Object restored = deserialize(serializedFormWithClassName(ModernDefaultInstanceMessage.getDefaultInstance()));

        assertInstanceOf(ModernDefaultInstanceMessage.class, restored);
    }

    @Test
    void resolvesSerializedFormUsingLegacyDefaultInstanceFallback() throws Exception {
        Object restored = deserialize(serializedFormWithClassName(LegacyDefaultInstanceMessage.getDefaultInstance()));

        assertInstanceOf(LegacyDefaultInstanceMessage.class, restored);
    }

    private static byte[] serializedFormWithClassName(MessageLite message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(STREAM_MAGIC);
        output.writeShort(STREAM_VERSION);
        output.writeByte(TC_OBJECT);
        writeSerializedFormClassDescriptor(output);
        writeSerializedByteArray(output, message.toByteArray());
        output.writeByte(TC_NULL);
        writeString(output, message.getClass().getName());
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeSerializedFormClassDescriptor(DataOutputStream output) throws IOException {
        output.writeByte(TC_CLASSDESC);
        output.writeUTF(SERIALIZED_FORM_CLASS_NAME);
        output.writeLong(0L);
        output.writeByte(SC_SERIALIZABLE);
        output.writeShort(3);
        writeField(output, '[', "asBytes", "[B");
        writeField(output, 'L', "messageClass", "Ljava/lang/Class;");
        writeField(output, 'L', "messageClassName", "Ljava/lang/String;");
        output.writeByte(TC_ENDBLOCKDATA);
        output.writeByte(TC_NULL);
    }

    private static void writeField(
            DataOutputStream output,
            char typeCode,
            String name,
            String typeName) throws IOException {
        output.writeByte(typeCode);
        output.writeUTF(name);
        writeString(output, typeName);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        output.writeByte(TC_STRING);
        output.writeUTF(value);
    }

    private static void writeSerializedByteArray(DataOutputStream output, byte[] value) throws IOException {
        byte[] serializedValue = serialize(value);
        output.write(serializedValue, STREAM_HEADER_LENGTH, serializedValue.length - STREAM_HEADER_LENGTH);
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

    public static final class ModernDefaultInstanceMessage extends GeneratedMessageLite<
            ModernDefaultInstanceMessage,
            ModernDefaultInstanceMessage.Builder> {
        private static final ModernDefaultInstanceMessage DEFAULT_INSTANCE = new ModernDefaultInstanceMessage();
        private static volatile Parser<ModernDefaultInstanceMessage> parser;

        static {
            registerDefaultInstance(ModernDefaultInstanceMessage.class, DEFAULT_INSTANCE);
        }

        private ModernDefaultInstanceMessage() {
        }

        static ModernDefaultInstanceMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ModernDefaultInstanceMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unknown method: " + method);
            }
        }

        private static Parser<ModernDefaultInstanceMessage> parser() {
            Parser<ModernDefaultInstanceMessage> localParser = parser;
            if (localParser == null) {
                synchronized (ModernDefaultInstanceMessage.class) {
                    localParser = parser;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = localParser;
                    }
                }
            }
            return localParser;
        }

        public static final class Builder extends GeneratedMessageLite.Builder<ModernDefaultInstanceMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class LegacyDefaultInstanceMessage extends GeneratedMessageLite<
            LegacyDefaultInstanceMessage,
            LegacyDefaultInstanceMessage.Builder> {
        @SuppressWarnings("java:S116")
        private static final LegacyDefaultInstanceMessage defaultInstance = new LegacyDefaultInstanceMessage();
        private static volatile Parser<LegacyDefaultInstanceMessage> parser;

        static {
            registerDefaultInstance(LegacyDefaultInstanceMessage.class, defaultInstance);
        }

        private LegacyDefaultInstanceMessage() {
        }

        static LegacyDefaultInstanceMessage getDefaultInstance() {
            return defaultInstance;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacyDefaultInstanceMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unknown method: " + method);
            }
        }

        private static Parser<LegacyDefaultInstanceMessage> parser() {
            Parser<LegacyDefaultInstanceMessage> localParser = parser;
            if (localParser == null) {
                synchronized (LegacyDefaultInstanceMessage.class) {
                    localParser = parser;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(defaultInstance);
                        parser = localParser;
                    }
                }
            }
            return localParser;
        }

        public static final class Builder extends GeneratedMessageLite.Builder<LegacyDefaultInstanceMessage, Builder> {
            private Builder() {
                super(defaultInstance);
            }
        }
    }
}
