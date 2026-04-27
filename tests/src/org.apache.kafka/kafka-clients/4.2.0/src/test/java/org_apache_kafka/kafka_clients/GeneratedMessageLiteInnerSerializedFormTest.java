/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.kafka.shaded.com.google.protobuf.AbstractParser;
import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.CodedOutputStream;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.MessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteInnerSerializedFormTest {

    @Test
    void readResolveUsesMessageClassNameAndDefaultInstanceFieldWhenSerializedClassIsUnavailable()
        throws IOException, ClassNotFoundException {
        DefaultInstanceMessage message = DefaultInstanceMessage.of("default-instance");

        Object resolved = deserializeSerializedFormWithNullMessageClass(
            SerializedFormAccessor.serializedFormOf(message),
            message.getClass()
        );

        assertThat(resolved).isInstanceOf(DefaultInstanceMessage.class).isEqualTo(message);
    }

    @Test
    void readResolveFallsBackToLegacyDefaultInstanceFieldName() throws IOException, ClassNotFoundException {
        LegacyDefaultInstanceMessage message = LegacyDefaultInstanceMessage.of("legacy-default-instance");

        Object resolved = deserializeSerializedFormWithNullMessageClass(
            SerializedFormAccessor.serializedFormOf(message),
            message.getClass()
        );

        assertThat(resolved).isInstanceOf(LegacyDefaultInstanceMessage.class).isEqualTo(message);
    }

    private static Object deserializeSerializedFormWithNullMessageClass(Object serializedForm, Class<?> messageClass)
        throws IOException, ClassNotFoundException {
        return deserialize(serializeWithNullMessageClass(serializedForm, messageClass));
    }

    private static byte[] serializeWithNullMessageClass(Object value, Class<?> messageClass) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output) {
            {
                enableReplaceObject(true);
            }

            @Override
            protected Object replaceObject(Object object) {
                if (object == messageClass) {
                    return null;
                }
                return object;
            }
        }) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private interface MessageFactory<T extends MessageLite> {
        T create(byte[] payload);
    }

    private abstract static class SerializedFormAccessor extends GeneratedMessageLite {
        private static Object serializedFormOf(MessageLite message) {
            return SerializedForm.of(message);
        }
    }

    private abstract static class AbstractTestMessage<
        MessageType extends AbstractTestMessage<MessageType, BuilderType>,
        BuilderType extends AbstractTestBuilder<MessageType, BuilderType>
    > implements MessageLite {
        private final byte[] payload;

        protected AbstractTestMessage(byte[] payload) {
            this.payload = Arrays.copyOf(payload, payload.length);
        }

        protected abstract MessageType createMessage(byte[] payload);

        protected abstract BuilderType createBuilder(byte[] payload);

        protected abstract MessageType defaultInstance();

        protected final byte[] payload() {
            return Arrays.copyOf(this.payload, this.payload.length);
        }

        @Override
        public final void writeTo(CodedOutputStream output) throws IOException {
            output.writeRawBytes(this.payload);
        }

        @Override
        public final int getSerializedSize() {
            return this.payload.length;
        }

        @Override
        public final Parser<MessageType> getParserForType() {
            return new RawBytesParser<>(this::createMessage);
        }

        @Override
        public final ByteString toByteString() {
            return ByteString.copyFrom(this.payload);
        }

        @Override
        public final byte[] toByteArray() {
            return payload();
        }

        @Override
        public final void writeTo(OutputStream output) throws IOException {
            output.write(this.payload);
        }

        @Override
        public final void writeDelimitedTo(OutputStream output) throws IOException {
            CodedOutputStream codedOutput = CodedOutputStream.newInstance(output);
            codedOutput.writeUInt32NoTag(this.payload.length);
            codedOutput.writeRawBytes(this.payload);
            codedOutput.flush();
        }

        @Override
        public final BuilderType newBuilderForType() {
            return createBuilder(new byte[0]);
        }

        @Override
        public final BuilderType toBuilder() {
            return createBuilder(this.payload);
        }

        @Override
        public final MessageType getDefaultInstanceForType() {
            return defaultInstance();
        }

        @Override
        public final boolean isInitialized() {
            return true;
        }

        @Override
        public final boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            AbstractTestMessage<?, ?> that = (AbstractTestMessage<?, ?>) other;
            return Arrays.equals(this.payload, that.payload);
        }

        @Override
        public final int hashCode() {
            return 31 * getClass().hashCode() + Arrays.hashCode(this.payload);
        }
    }

    private abstract static class AbstractTestBuilder<
        MessageType extends AbstractTestMessage<MessageType, BuilderType>,
        BuilderType extends AbstractTestBuilder<MessageType, BuilderType>
    > implements MessageLite.Builder {
        private byte[] payload;

        protected AbstractTestBuilder(byte[] payload) {
            this.payload = Arrays.copyOf(payload, payload.length);
        }

        protected abstract BuilderType self();

        protected abstract BuilderType createBuilder(byte[] payload);

        protected abstract MessageType createMessage(byte[] payload);

        protected abstract MessageType defaultInstance();

        @Override
        public final BuilderType clear() {
            this.payload = new byte[0];
            return self();
        }

        @Override
        public final MessageType build() {
            return buildPartial();
        }

        @Override
        public final MessageType buildPartial() {
            return createMessage(this.payload);
        }

        @Override
        public final BuilderType clone() {
            return createBuilder(this.payload);
        }

        @Override
        public final BuilderType mergeFrom(CodedInputStream input) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public final BuilderType mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public final BuilderType mergeFrom(ByteString data) throws InvalidProtocolBufferException {
            return mergeFrom(data.toByteArray());
        }

        @Override
        public final BuilderType mergeFrom(ByteString data, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
            return mergeFrom(data);
        }

        @Override
        public final BuilderType mergeFrom(byte[] data) {
            this.payload = Arrays.copyOf(data, data.length);
            return self();
        }

        @Override
        public final BuilderType mergeFrom(byte[] data, int offset, int length) {
            this.payload = Arrays.copyOfRange(data, offset, offset + length);
            return self();
        }

        @Override
        public final BuilderType mergeFrom(byte[] data, ExtensionRegistryLite extensionRegistry) {
            return mergeFrom(data);
        }

        @Override
        public final BuilderType mergeFrom(byte[] data, int offset, int length, ExtensionRegistryLite extensionRegistry) {
            return mergeFrom(data, offset, length);
        }

        @Override
        public final BuilderType mergeFrom(InputStream input) throws IOException {
            this.payload = input.readAllBytes();
            return self();
        }

        @Override
        public final BuilderType mergeFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return mergeFrom(input);
        }

        @Override
        public final BuilderType mergeFrom(MessageLite other) {
            AbstractTestMessage<?, ?> message = (AbstractTestMessage<?, ?>) other;
            this.payload = message.payload();
            return self();
        }

        @Override
        public final boolean mergeDelimitedFrom(InputStream input) throws IOException {
            byte[] delimitedPayload = input.readAllBytes();
            if (delimitedPayload.length == 0) {
                return false;
            }
            this.payload = delimitedPayload;
            return true;
        }

        @Override
        public final boolean mergeDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
            throws IOException {
            return mergeDelimitedFrom(input);
        }

        @Override
        public final MessageType getDefaultInstanceForType() {
            return defaultInstance();
        }

        @Override
        public final boolean isInitialized() {
            return true;
        }
    }

    private static final class RawBytesParser<MessageType extends MessageLite> extends AbstractParser<MessageType> {
        private final MessageFactory<MessageType> messageFactory;

        private RawBytesParser(MessageFactory<MessageType> messageFactory) {
            this.messageFactory = messageFactory;
        }

        @Override
        public MessageType parsePartialFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
            try {
                int bytesUntilLimit = input.getBytesUntilLimit();
                byte[] payload = bytesUntilLimit >= 0 ? input.readRawBytes(bytesUntilLimit) : new byte[0];
                return this.messageFactory.create(payload);
            } catch (IOException e) {
                throw new InvalidProtocolBufferException(e);
            }
        }
    }

    private static final class DefaultInstanceMessage
        extends AbstractTestMessage<DefaultInstanceMessage, DefaultInstanceMessageBuilder> {
        private static final DefaultInstanceMessage DEFAULT_INSTANCE = new DefaultInstanceMessage(new byte[0]);

        private DefaultInstanceMessage(byte[] payload) {
            super(payload);
        }

        private static DefaultInstanceMessage of(String payload) {
            return new DefaultInstanceMessage(payload.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected DefaultInstanceMessage createMessage(byte[] payload) {
            return new DefaultInstanceMessage(payload);
        }

        @Override
        protected DefaultInstanceMessageBuilder createBuilder(byte[] payload) {
            return new DefaultInstanceMessageBuilder(payload);
        }

        @Override
        protected DefaultInstanceMessage defaultInstance() {
            return DEFAULT_INSTANCE;
        }
    }

    private static final class DefaultInstanceMessageBuilder
        extends AbstractTestBuilder<DefaultInstanceMessage, DefaultInstanceMessageBuilder> {

        private DefaultInstanceMessageBuilder(byte[] payload) {
            super(payload);
        }

        @Override
        protected DefaultInstanceMessageBuilder self() {
            return this;
        }

        @Override
        protected DefaultInstanceMessageBuilder createBuilder(byte[] payload) {
            return new DefaultInstanceMessageBuilder(payload);
        }

        @Override
        protected DefaultInstanceMessage createMessage(byte[] payload) {
            return new DefaultInstanceMessage(payload);
        }

        @Override
        protected DefaultInstanceMessage defaultInstance() {
            return DefaultInstanceMessage.DEFAULT_INSTANCE;
        }
    }

    private static final class LegacyDefaultInstanceMessage
        extends AbstractTestMessage<LegacyDefaultInstanceMessage, LegacyDefaultInstanceMessageBuilder> {
        private static final LegacyDefaultInstanceMessage defaultInstance = new LegacyDefaultInstanceMessage(new byte[0]);

        private LegacyDefaultInstanceMessage(byte[] payload) {
            super(payload);
        }

        private static LegacyDefaultInstanceMessage of(String payload) {
            return new LegacyDefaultInstanceMessage(payload.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected LegacyDefaultInstanceMessage createMessage(byte[] payload) {
            return new LegacyDefaultInstanceMessage(payload);
        }

        @Override
        protected LegacyDefaultInstanceMessageBuilder createBuilder(byte[] payload) {
            return new LegacyDefaultInstanceMessageBuilder(payload);
        }

        @Override
        protected LegacyDefaultInstanceMessage defaultInstance() {
            return defaultInstance;
        }
    }

    private static final class LegacyDefaultInstanceMessageBuilder
        extends AbstractTestBuilder<LegacyDefaultInstanceMessage, LegacyDefaultInstanceMessageBuilder> {

        private LegacyDefaultInstanceMessageBuilder(byte[] payload) {
            super(payload);
        }

        @Override
        protected LegacyDefaultInstanceMessageBuilder self() {
            return this;
        }

        @Override
        protected LegacyDefaultInstanceMessageBuilder createBuilder(byte[] payload) {
            return new LegacyDefaultInstanceMessageBuilder(payload);
        }

        @Override
        protected LegacyDefaultInstanceMessage createMessage(byte[] payload) {
            return new LegacyDefaultInstanceMessage(payload);
        }

        @Override
        protected LegacyDefaultInstanceMessage defaultInstance() {
            return LegacyDefaultInstanceMessage.defaultInstance;
        }
    }
}
