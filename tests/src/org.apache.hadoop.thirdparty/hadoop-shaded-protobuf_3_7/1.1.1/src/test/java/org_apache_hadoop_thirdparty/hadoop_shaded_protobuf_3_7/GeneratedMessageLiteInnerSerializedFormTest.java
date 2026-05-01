/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.hadoop.thirdparty.protobuf.ByteString;
import org.apache.hadoop.thirdparty.protobuf.CodedInputStream;
import org.apache.hadoop.thirdparty.protobuf.CodedOutputStream;
import org.apache.hadoop.thirdparty.protobuf.ExtensionRegistryLite;
import org.apache.hadoop.thirdparty.protobuf.GeneratedMessageLite;
import org.apache.hadoop.thirdparty.protobuf.InvalidProtocolBufferException;
import org.apache.hadoop.thirdparty.protobuf.MessageLite;
import org.apache.hadoop.thirdparty.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteInnerSerializedFormTest {
    private static final byte[] MODERN_PAYLOAD = new byte[] {1, 3, 5, 7};
    private static final byte[] LEGACY_PAYLOAD = new byte[] {2, 4, 6, 8};

    @Test
    void readResolveRestoresMessageThroughDefaultInstanceField() throws Exception {
        Object restored = serializeFormAndReadBackWithClassNameLookup(new ModernLiteMessage(MODERN_PAYLOAD));

        assertThat(restored).isInstanceOf(ModernLiteMessage.class);
        assertThat(((ModernLiteMessage) restored).payload()).containsExactly(MODERN_PAYLOAD);
    }

    @Test
    void readResolveFallsBackToLegacyDefaultInstanceField() throws Exception {
        Object restored = serializeFormAndReadBackWithClassNameLookup(new LegacyLiteMessage(LEGACY_PAYLOAD));

        assertThat(restored).isInstanceOf(LegacyLiteMessage.class);
        assertThat(((LegacyLiteMessage) restored).payload()).containsExactly(LEGACY_PAYLOAD);
    }

    private static Object serializeFormAndReadBackWithClassNameLookup(MessageLite message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ClassNullingObjectOutputStream output = new ClassNullingObjectOutputStream(bytes)) {
            output.writeObject(SerializedFormAccess.serializedFormOf(message));
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    @SuppressWarnings("rawtypes")
    private abstract static class SerializedFormAccess extends GeneratedMessageLite {
        static Object serializedFormOf(MessageLite message) {
            return SerializedForm.of(message);
        }
    }

    private static class ClassNullingObjectOutputStream extends ObjectOutputStream {
        ClassNullingObjectOutputStream(OutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object == ModernLiteMessage.class || object == LegacyLiteMessage.class) {
                return null;
            }
            return super.replaceObject(object);
        }
    }

    private abstract static class BaseLiteMessage implements MessageLite {
        private final byte[] payload;

        BaseLiteMessage(byte[] payload) {
            this.payload = Arrays.copyOf(payload, payload.length);
        }

        byte[] payload() {
            return Arrays.copyOf(payload, payload.length);
        }

        @Override
        public void writeTo(CodedOutputStream output) throws IOException {
            output.writeRawBytes(payload);
        }

        @Override
        public int getSerializedSize() {
            return payload.length;
        }

        @Override
        public Parser<? extends MessageLite> getParserForType() {
            throw new UnsupportedOperationException("Parsing is exercised through the builder");
        }

        @Override
        public ByteString toByteString() {
            return ByteString.copyFrom(payload);
        }

        @Override
        public byte[] toByteArray() {
            return payload();
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            output.write(payload);
        }

        @Override
        public void writeDelimitedTo(OutputStream output) throws IOException {
            output.write(payload.length);
            output.write(payload);
        }

        @Override
        public boolean isInitialized() {
            return true;
        }
    }

    private static final class ModernLiteMessage extends BaseLiteMessage {
        @SuppressWarnings("unused")
        private static final ModernLiteMessage DEFAULT_INSTANCE = new ModernLiteMessage(new byte[0]);

        ModernLiteMessage(byte[] payload) {
            super(payload);
        }

        @Override
        public MessageLite.Builder newBuilderForType() {
            return new ModernBuilder();
        }

        @Override
        public MessageLite.Builder toBuilder() {
            return new ModernBuilder().mergeFrom(payload());
        }

        @Override
        public MessageLite getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }
    }

    private static final class LegacyLiteMessage extends BaseLiteMessage {
        @SuppressWarnings({"checkstyle:ConstantName", "unused"})
        private static final LegacyLiteMessage defaultInstance = new LegacyLiteMessage(new byte[0]);

        LegacyLiteMessage(byte[] payload) {
            super(payload);
        }

        @Override
        public MessageLite.Builder newBuilderForType() {
            return new LegacyBuilder();
        }

        @Override
        public MessageLite.Builder toBuilder() {
            return new LegacyBuilder().mergeFrom(payload());
        }

        @Override
        public MessageLite getDefaultInstanceForType() {
            return defaultInstance;
        }
    }

    private abstract static class BaseBuilder implements MessageLite.Builder {
        private byte[] payload = new byte[0];

        @Override
        public MessageLite.Builder clear() {
            payload = new byte[0];
            return this;
        }

        @Override
        public MessageLite build() {
            return buildPartial();
        }

        @Override
        public MessageLite.Builder clone() {
            BaseBuilder clone = newBuilder();
            clone.payload = Arrays.copyOf(payload, payload.length);
            return clone;
        }

        @Override
        public MessageLite.Builder mergeFrom(CodedInputStream input) throws IOException {
            return mergeFrom(input, ExtensionRegistryLite.getEmptyRegistry());
        }

        @Override
        public MessageLite.Builder mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (!input.isAtEnd()) {
                bytes.write(input.readRawByte());
            }
            return mergeFrom(bytes.toByteArray());
        }

        @Override
        public MessageLite.Builder mergeFrom(ByteString data) throws InvalidProtocolBufferException {
            return mergeFrom(data.toByteArray());
        }

        @Override
        public MessageLite.Builder mergeFrom(ByteString data, ExtensionRegistryLite extensionRegistry)
                throws InvalidProtocolBufferException {
            return mergeFrom(data);
        }

        @Override
        public MessageLite.Builder mergeFrom(byte[] data) {
            payload = Arrays.copyOf(data, data.length);
            return this;
        }

        @Override
        public MessageLite.Builder mergeFrom(byte[] data, int offset, int length) {
            payload = Arrays.copyOfRange(data, offset, offset + length);
            return this;
        }

        @Override
        public MessageLite.Builder mergeFrom(byte[] data, ExtensionRegistryLite extensionRegistry) {
            return mergeFrom(data);
        }

        @Override
        public MessageLite.Builder mergeFrom(byte[] data, int offset, int length,
                ExtensionRegistryLite extensionRegistry) {
            return mergeFrom(data, offset, length);
        }

        @Override
        public MessageLite.Builder mergeFrom(InputStream input) throws IOException {
            return mergeFrom(input.readAllBytes());
        }

        @Override
        public MessageLite.Builder mergeFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
                throws IOException {
            return mergeFrom(input);
        }

        @Override
        public MessageLite.Builder mergeFrom(MessageLite message) {
            return mergeFrom(message.toByteArray());
        }

        @Override
        public boolean mergeDelimitedFrom(InputStream input) throws IOException {
            int length = input.read();
            if (length < 0) {
                return false;
            }
            return mergeFrom(input.readNBytes(length)) != null;
        }

        @Override
        public boolean mergeDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
                throws IOException {
            return mergeDelimitedFrom(input);
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        byte[] payload() {
            return Arrays.copyOf(payload, payload.length);
        }

        abstract BaseBuilder newBuilder();
    }

    private static final class ModernBuilder extends BaseBuilder {
        @Override
        public MessageLite buildPartial() {
            return new ModernLiteMessage(payload());
        }

        @Override
        public MessageLite getDefaultInstanceForType() {
            return ModernLiteMessage.DEFAULT_INSTANCE;
        }

        @Override
        BaseBuilder newBuilder() {
            return new ModernBuilder();
        }
    }

    private static final class LegacyBuilder extends BaseBuilder {
        @Override
        public MessageLite buildPartial() {
            return new LegacyLiteMessage(payload());
        }

        @Override
        public MessageLite getDefaultInstanceForType() {
            return LegacyLiteMessage.defaultInstance;
        }

        @Override
        BaseBuilder newBuilder() {
            return new LegacyBuilder();
        }
    }
}
