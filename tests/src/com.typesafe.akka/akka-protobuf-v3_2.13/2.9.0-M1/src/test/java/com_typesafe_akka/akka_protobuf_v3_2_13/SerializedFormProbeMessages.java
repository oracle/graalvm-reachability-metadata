/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import akka.protobufv3.internal.ByteString;
import akka.protobufv3.internal.CodedInputStream;
import akka.protobufv3.internal.CodedOutputStream;
import akka.protobufv3.internal.ExtensionRegistryLite;
import akka.protobufv3.internal.InvalidProtocolBufferException;
import akka.protobufv3.internal.MessageLite;
import akka.protobufv3.internal.Parser;

final class DefaultInstanceLiteMessage extends BaseLiteMessage {
    public static final DefaultInstanceLiteMessage DEFAULT_INSTANCE = new DefaultInstanceLiteMessage(new byte[0]);

    DefaultInstanceLiteMessage(byte[] payload) {
        super(payload);
    }

    @Override
    public MessageLite.Builder newBuilderForType() {
        return new DefaultInstanceBuilder();
    }

    @Override
    public MessageLite.Builder toBuilder() {
        return new DefaultInstanceBuilder().mergeFrom(payload());
    }

    @Override
    public MessageLite getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }
}

final class LegacyDefaultInstanceLiteMessage extends BaseLiteMessage {
    @SuppressWarnings("checkstyle:ConstantName")
    public static final LegacyDefaultInstanceLiteMessage defaultInstance =
            new LegacyDefaultInstanceLiteMessage(new byte[0]);

    LegacyDefaultInstanceLiteMessage(byte[] payload) {
        super(payload);
    }

    @Override
    public MessageLite.Builder newBuilderForType() {
        return new LegacyDefaultInstanceBuilder();
    }

    @Override
    public MessageLite.Builder toBuilder() {
        return new LegacyDefaultInstanceBuilder().mergeFrom(payload());
    }

    @Override
    public MessageLite getDefaultInstanceForType() {
        return defaultInstance;
    }
}

abstract class BaseLiteMessage implements MessageLite {
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

abstract class BaseLiteMessageBuilder implements MessageLite.Builder {
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
        BaseLiteMessageBuilder clone = newBuilder();
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
    public MessageLite.Builder mergeFrom(byte[] data, int offset, int length, ExtensionRegistryLite extensionRegistry) {
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
    public boolean mergeDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
        return mergeDelimitedFrom(input);
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    abstract BaseLiteMessageBuilder newBuilder();
}

final class DefaultInstanceBuilder extends BaseLiteMessageBuilder {
    @Override
    public MessageLite buildPartial() {
        return new DefaultInstanceLiteMessage(payload());
    }

    @Override
    public MessageLite getDefaultInstanceForType() {
        return DefaultInstanceLiteMessage.DEFAULT_INSTANCE;
    }

    @Override
    BaseLiteMessageBuilder newBuilder() {
        return new DefaultInstanceBuilder();
    }
}

final class LegacyDefaultInstanceBuilder extends BaseLiteMessageBuilder {
    @Override
    public MessageLite buildPartial() {
        return new LegacyDefaultInstanceLiteMessage(payload());
    }

    @Override
    public MessageLite getDefaultInstanceForType() {
        return LegacyDefaultInstanceLiteMessage.defaultInstance;
    }

    @Override
    BaseLiteMessageBuilder newBuilder() {
        return new LegacyDefaultInstanceBuilder();
    }
}
