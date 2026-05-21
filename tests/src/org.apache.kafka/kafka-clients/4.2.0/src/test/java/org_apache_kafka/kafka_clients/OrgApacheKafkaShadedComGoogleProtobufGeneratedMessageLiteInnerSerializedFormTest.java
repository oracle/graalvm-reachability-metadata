/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteInnerSerializedFormTest {
    private static final String SERIALIZED_FORM_CLASS_NAME =
            "org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite$SerializedForm";
    private static final long BYTE_ARRAY_SERIAL_VERSION_UID = -5984413125824719648L;
    private static final byte TC_NULL = 0x70;
    private static final byte TC_OBJECT = 0x73;
    private static final byte TC_STRING = 0x74;
    private static final byte TC_ARRAY = 0x75;
    private static final byte TC_CLASSDESC = 0x72;
    private static final byte TC_ENDBLOCKDATA = 0x78;
    private static final byte SC_SERIALIZABLE = 0x02;

    @Test
    @Timeout(30)
    void legacySerializedFormResolvesMessageClassByNameAndUsesDefaultInstanceField() throws Exception {
        byte[] serializedForm = legacySerializedForm(DefaultInstanceLiteMessage.class.getName(), new byte[0]);

        Object roundTripped = deserialize(serializedForm);

        assertThat(roundTripped).isInstanceOf(DefaultInstanceLiteMessage.class);
    }

    @Test
    @Timeout(30)
    void legacySerializedFormFallsBackToOldDefaultInstanceFieldName() throws Exception {
        byte[] serializedForm = legacySerializedForm(LegacyDefaultInstanceLiteMessage.class.getName(), new byte[0]);

        Object roundTripped = deserialize(serializedForm);

        assertThat(roundTripped).isInstanceOf(LegacyDefaultInstanceLiteMessage.class);
    }

    private static Object deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    private static byte[] legacySerializedForm(String messageClassName, byte[] asBytes) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(0xaced);
        output.writeShort(5);
        output.writeByte(TC_OBJECT);
        writeSerializedFormClassDescription(output);
        writeByteArray(output, asBytes);
        output.writeByte(TC_STRING);
        output.writeUTF(messageClassName);
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeSerializedFormClassDescription(DataOutputStream output) throws Exception {
        output.writeByte(TC_CLASSDESC);
        output.writeUTF(SERIALIZED_FORM_CLASS_NAME);
        output.writeLong(0L);
        output.writeByte(SC_SERIALIZABLE);
        output.writeShort(2);
        output.writeByte('[');
        output.writeUTF("asBytes");
        output.writeByte(TC_STRING);
        output.writeUTF("[B");
        output.writeByte('L');
        output.writeUTF("messageClassName");
        output.writeByte(TC_STRING);
        output.writeUTF("Ljava/lang/String;");
        output.writeByte(TC_ENDBLOCKDATA);
        output.writeByte(TC_NULL);
    }

    private static void writeByteArray(DataOutputStream output, byte[] bytes) throws Exception {
        output.writeByte(TC_ARRAY);
        output.writeByte(TC_CLASSDESC);
        output.writeUTF("[B");
        output.writeLong(BYTE_ARRAY_SERIAL_VERSION_UID);
        output.writeByte(SC_SERIALIZABLE);
        output.writeShort(0);
        output.writeByte(TC_ENDBLOCKDATA);
        output.writeByte(TC_NULL);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    public static final class DefaultInstanceLiteMessage
            extends GeneratedMessageLite<DefaultInstanceLiteMessage, DefaultInstanceLiteMessage.Builder> {
        private static final DefaultInstanceLiteMessage DEFAULT_INSTANCE = new DefaultInstanceLiteMessage();

        static {
            registerDefaultInstance(DefaultInstanceLiteMessage.class, DEFAULT_INSTANCE);
        }

        private DefaultInstanceLiteMessage() {
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new DefaultInstanceLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                case GET_PARSER:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported protobuf operation: " + method);
            }
        }

        public static final class Builder
                extends GeneratedMessageLite.Builder<DefaultInstanceLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class LegacyDefaultInstanceLiteMessage
            extends GeneratedMessageLite<LegacyDefaultInstanceLiteMessage, LegacyDefaultInstanceLiteMessage.Builder> {
        private static final LegacyDefaultInstanceLiteMessage defaultInstance = new LegacyDefaultInstanceLiteMessage();

        static {
            registerDefaultInstance(LegacyDefaultInstanceLiteMessage.class, defaultInstance);
        }

        private LegacyDefaultInstanceLiteMessage() {
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacyDefaultInstanceLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                case GET_PARSER:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported protobuf operation: " + method);
            }
        }

        public static final class Builder
                extends GeneratedMessageLite.Builder<LegacyDefaultInstanceLiteMessage, Builder> {
            private Builder() {
                super(defaultInstance);
            }
        }
    }
}
