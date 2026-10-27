/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.AbstractMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.AbstractParser;
import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.CodedOutputStream;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.MessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteInnerSerializedFormTest {

    private static final String SERIALIZED_FORM_CLASS_NAME =
            "org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite$SerializedForm";
    private static final long SERIAL_VERSION_UID = 0L;
    private static final byte STREAM_MAGIC_HIGH = (byte) 0xAC;
    private static final byte STREAM_MAGIC_LOW = (byte) 0xED;
    private static final byte TC_OBJECT = 0x73;
    private static final byte TC_CLASSDESC = 0x72;
    private static final byte TC_STRING = 0x74;
    private static final byte TC_ENDBLOCKDATA = 0x78;
    private static final byte TC_NULL = 0x70;
    private static final byte SC_SERIALIZABLE = 0x02;

    @Test
    void deserializesModernSerializedFormUsingDefaultInstanceField() throws Exception {
        DefaultInstanceMessage message = DefaultInstanceMessage.of(42);

        Object resolved = deserialize(serializedFormBytes(
                DefaultInstanceMessage.class,
                DefaultInstanceMessage.class.getName(),
                message.toByteArray()));

        assertThat(resolved).isInstanceOf(DefaultInstanceMessage.class);
        DefaultInstanceMessage resolvedMessage = (DefaultInstanceMessage) resolved;
        assertThat(resolvedMessage.getValue()).isEqualTo(42);
    }

    @Test
    void deserializesLegacySerializedFormUsingClassNameAndFallbackDefaultInstanceField() throws Exception {
        LowerCaseDefaultInstanceMessage message = LowerCaseDefaultInstanceMessage.of(77);
        byte[] serializedForm = legacySerializedFormBytes(
                LowerCaseDefaultInstanceMessage.class.getName(),
                message.toByteArray());

        Object resolved = deserialize(serializedForm);

        assertThat(resolved).isInstanceOf(LowerCaseDefaultInstanceMessage.class);
        LowerCaseDefaultInstanceMessage resolvedMessage = (LowerCaseDefaultInstanceMessage) resolved;
        assertThat(resolvedMessage.getValue()).isEqualTo(77);
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static byte[] legacySerializedFormBytes(String messageClassName, byte[] asBytes) throws IOException {
        return serializedFormBytes(null, messageClassName, asBytes);
    }

    private static byte[] serializedFormBytes(Class<?> messageClass, String messageClassName, byte[] asBytes)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(STREAM_MAGIC_HIGH);
            output.writeByte(STREAM_MAGIC_LOW);
            output.writeShort(5);
            output.writeByte(TC_OBJECT);
            output.writeByte(TC_CLASSDESC);
            output.writeUTF(SERIALIZED_FORM_CLASS_NAME);
            output.writeLong(SERIAL_VERSION_UID);
            output.writeByte(SC_SERIALIZABLE);
            output.writeShort(messageClass == null ? 2 : 3);
            output.writeByte('[');
            output.writeUTF("asBytes");
            writeSerializedString(output, "[B");
            if (messageClass != null) {
                output.writeByte('L');
                output.writeUTF("messageClass");
                writeSerializedString(output, "Ljava/lang/Class;");
            }
            output.writeByte('L');
            output.writeUTF("messageClassName");
            writeSerializedString(output, "Ljava/lang/String;");
            output.writeByte(TC_ENDBLOCKDATA);
            output.writeByte(TC_NULL);
            output.write(serializedObjectPayload(asBytes));
            if (messageClass != null) {
                output.write(serializedObjectPayload(messageClass));
            }
            output.write(serializedObjectPayload(messageClassName));
        }
        return bytes.toByteArray();
    }

    private static void writeSerializedString(DataOutputStream output, String value) throws IOException {
        output.writeByte(TC_STRING);
        output.writeUTF(value);
    }

    private static byte[] serializedObjectPayload(Object value) throws IOException {
        byte[] serialized = serialize(value);
        return Arrays.copyOfRange(serialized, 4, serialized.length);
    }

    public static final class DefaultInstanceMessage
            extends TestMessage<DefaultInstanceMessage, DefaultInstanceMessage.Builder> {
        private static final DefaultInstanceMessage DEFAULT_INSTANCE = new DefaultInstanceMessage(0);
        private static final Parser<DefaultInstanceMessage> PARSER =
                new TestMessageParser<>(DefaultInstanceMessage::of);

        private DefaultInstanceMessage(int value) {
            super(value);
        }

        public static DefaultInstanceMessage of(int value) {
            return new DefaultInstanceMessage(value);
        }

        @Override
        public Parser<DefaultInstanceMessage> getParserForType() {
            return PARSER;
        }

        @Override
        public Builder newBuilderForType() {
            return new Builder();
        }

        @Override
        public Builder toBuilder() {
            return new Builder().setValue(getValue());
        }

        @Override
        public DefaultInstanceMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        public static final class Builder extends TestMessageBuilder<DefaultInstanceMessage, Builder> {
            @Override
            public DefaultInstanceMessage buildPartial() {
                return DefaultInstanceMessage.of(value);
            }

            @Override
            protected Builder self() {
                return this;
            }
        }
    }

    public static final class LowerCaseDefaultInstanceMessage
            extends TestMessage<LowerCaseDefaultInstanceMessage, LowerCaseDefaultInstanceMessage.Builder> {
        private static final LowerCaseDefaultInstanceMessage defaultInstance = new LowerCaseDefaultInstanceMessage(0);
        private static final Parser<LowerCaseDefaultInstanceMessage> PARSER =
                new TestMessageParser<>(LowerCaseDefaultInstanceMessage::of);

        private LowerCaseDefaultInstanceMessage(int value) {
            super(value);
        }

        public static LowerCaseDefaultInstanceMessage of(int value) {
            return new LowerCaseDefaultInstanceMessage(value);
        }

        @Override
        public Parser<LowerCaseDefaultInstanceMessage> getParserForType() {
            return PARSER;
        }

        @Override
        public Builder newBuilderForType() {
            return new Builder();
        }

        @Override
        public Builder toBuilder() {
            return new Builder().setValue(getValue());
        }

        @Override
        public LowerCaseDefaultInstanceMessage getDefaultInstanceForType() {
            return defaultInstance;
        }

        public static final class Builder extends TestMessageBuilder<LowerCaseDefaultInstanceMessage, Builder> {
            @Override
            public LowerCaseDefaultInstanceMessage buildPartial() {
                return LowerCaseDefaultInstanceMessage.of(value);
            }

            @Override
            protected Builder self() {
                return this;
            }
        }
    }

    private abstract static class TestMessage<MessageT extends TestMessage<MessageT, BuilderT>,
            BuilderT extends TestMessageBuilder<MessageT, BuilderT>>
            extends AbstractMessageLite<MessageT, BuilderT> {
        private final int value;

        TestMessage(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }

        @Override
        public void writeTo(CodedOutputStream output) throws IOException {
            if (value != 0) {
                output.writeInt32(1, value);
            }
        }

        @Override
        public int getSerializedSize() {
            if (value == 0) {
                return 0;
            }
            return CodedOutputStream.computeInt32Size(1, value);
        }

        @Override
        public boolean isInitialized() {
            return true;
        }
    }

    private abstract static class TestMessageBuilder<MessageT extends TestMessage<MessageT, BuilderT>,
            BuilderT extends TestMessageBuilder<MessageT, BuilderT>>
            extends AbstractMessageLite.Builder<MessageT, BuilderT> {
        protected int value;

        @Override
        public BuilderT clear() {
            value = 0;
            return self();
        }

        BuilderT setValue(int value) {
            this.value = value;
            return self();
        }

        @Override
        public MessageT build() {
            return buildPartial();
        }

        @Override
        public abstract MessageT buildPartial();

        @Override
        public BuilderT clone() {
            return self().setValue(value);
        }

        @Override
        public BuilderT mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            while (true) {
                int tag = input.readTag();
                if (tag == 0) {
                    return self();
                }
                if (tag == 8) {
                    value = input.readInt32();
                } else if (!input.skipField(tag)) {
                    return self();
                }
            }
        }

        @Override
        protected BuilderT internalMergeFrom(MessageT message) {
            value = message.getValue();
            return self();
        }

        @Override
        public MessageLite getDefaultInstanceForType() {
            return buildPartial().getDefaultInstanceForType();
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        protected abstract BuilderT self();
    }

    private interface MessageFactory<MessageT extends MessageLite> {
        MessageT create(int value);
    }

    private static final class TestMessageParser<MessageT extends TestMessage<?, ?>> extends AbstractParser<MessageT> {
        private final MessageFactory<MessageT> factory;

        private TestMessageParser(MessageFactory<MessageT> factory) {
            this.factory = factory;
        }

        @Override
        public MessageT parsePartialFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                throws InvalidProtocolBufferException {
            try {
                int value = 0;
                while (true) {
                    int tag = input.readTag();
                    if (tag == 0) {
                        return factory.create(value);
                    }
                    if (tag == 8) {
                        value = input.readInt32();
                    } else if (!input.skipField(tag)) {
                        return factory.create(value);
                    }
                }
            } catch (IOException e) {
                throw new InvalidProtocolBufferException(e);
            }
        }
    }
}
