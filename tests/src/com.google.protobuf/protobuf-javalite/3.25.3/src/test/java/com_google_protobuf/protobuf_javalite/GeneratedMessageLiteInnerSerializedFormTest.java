/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Empty;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteInnerSerializedFormTest {
    private static final String SERIALIZED_FORM_CLASS_NAME =
            "com.google.protobuf.GeneratedMessageLite$SerializedForm";

    @Test
    void readResolveUsesDefaultInstanceFieldForModernLiteMessages() throws Exception {
        Object resolved = deserializeLegacySerializedFormFor(Empty.getDefaultInstance());

        assertThat(resolved).isInstanceOf(Empty.class);
        assertThat(resolved).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void readResolveFallbackUsesLowerCaseDefaultInstanceFieldForLegacyLiteMessages()
            throws Exception {
        Object resolved = deserializeLegacySerializedFormFor(
                LegacyLiteMessage.getDefaultInstance()
        );

        assertThat(resolved).isInstanceOf(LegacyLiteMessage.class);
        assertThat(((MessageLite) resolved).toByteArray()).isEmpty();
    }

    private static Object deserializeLegacySerializedFormFor(MessageLite message)
            throws IOException, ClassNotFoundException {
        assertThat(SerializedFormReachability.serializedFormOf(message)).isNotNull();
        LegacySerializedForm legacySerializedForm = new LegacySerializedForm(message);
        byte[] serialized = serialize(legacySerializedForm);
        byte[] serializedAsProtobufForm = replaceSerializedClassName(
                serialized,
                LegacySerializedForm.class.getName(),
                SERIALIZED_FORM_CLASS_NAME
        );
        ByteArrayInputStream bytes = new ByteArrayInputStream(serializedAsProtobufForm);
        try (ObjectInputStream input = new ObjectInputStream(bytes)) {
            return input.readObject();
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static byte[] replaceSerializedClassName(
            byte[] stream,
            String oldName,
            String newName
    ) {
        byte[] oldNameBytes = oldName.getBytes(StandardCharsets.UTF_8);
        byte[] newNameBytes = newName.getBytes(StandardCharsets.UTF_8);
        int oldNameOffset = indexOf(stream, oldNameBytes);
        assertThat(oldNameOffset).isGreaterThan(1);
        assertThat(indexOf(stream, oldNameBytes, oldNameOffset + 1)).isEqualTo(-1);
        assertThat(readUnsignedShort(stream, oldNameOffset - 2)).isEqualTo(oldNameBytes.length);

        ByteArrayOutputStream replaced = new ByteArrayOutputStream(
                stream.length - oldNameBytes.length + newNameBytes.length
        );
        replaced.write(stream, 0, oldNameOffset - 2);
        replaced.write((newNameBytes.length >>> 8) & 0xFF);
        replaced.write(newNameBytes.length & 0xFF);
        replaced.writeBytes(newNameBytes);
        replaced.write(stream, oldNameOffset + oldNameBytes.length,
                stream.length - oldNameOffset - oldNameBytes.length);
        return replaced.toByteArray();
    }

    private static int indexOf(byte[] bytes, byte[] pattern) {
        return indexOf(bytes, pattern, 0);
    }

    private static int indexOf(byte[] bytes, byte[] pattern, int fromIndex) {
        for (int offset = fromIndex; offset <= bytes.length - pattern.length; offset++) {
            if (matchesAt(bytes, pattern, offset)) {
                return offset;
            }
        }
        return -1;
    }

    private static boolean matchesAt(byte[] bytes, byte[] pattern, int offset) {
        return Arrays.equals(
                Arrays.copyOfRange(bytes, offset, offset + pattern.length),
                pattern
        );
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}

abstract class SerializedFormReachability extends GeneratedMessageLite<Empty, Empty.Builder> {
    static Serializable serializedFormOf(MessageLite message) {
        return SerializedForm.of(message);
    }
}

final class LegacySerializedForm implements Serializable {
    private static final long serialVersionUID = 0L;

    private final String messageClassName;
    private final byte[] asBytes;

    LegacySerializedForm(MessageLite message) {
        messageClassName = message.getClass().getName();
        asBytes = message.toByteArray();
    }
}

final class LegacyLiteMessage
        extends GeneratedMessageLite<LegacyLiteMessage, LegacyLiteMessage.Builder> {
    public static final LegacyLiteMessage defaultInstance;
    private static volatile Parser<LegacyLiteMessage> parser;

    static {
        LegacyLiteMessage instance = new LegacyLiteMessage();
        defaultInstance = instance;
        registerDefaultInstance(LegacyLiteMessage.class, instance);
    }

    private LegacyLiteMessage() {
    }

    static LegacyLiteMessage getDefaultInstance() {
        return defaultInstance;
    }

    @Override
    @SuppressWarnings("fallthrough")
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new LegacyLiteMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(defaultInstance, "\u0000\u0000", null);
            case GET_DEFAULT_INSTANCE:
                return defaultInstance;
            case GET_PARSER:
                Parser<LegacyLiteMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (LegacyLiteMessage.class) {
                        localParser = parser;
                        if (localParser == null) {
                            localParser = new DefaultInstanceBasedParser<>(defaultInstance);
                            parser = localParser;
                        }
                    }
                }
                return localParser;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static final class Builder
            extends GeneratedMessageLite.Builder<LegacyLiteMessage, Builder> {
        private Builder() {
            super(defaultInstance);
        }

        @Override
        public Builder mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                throws IOException {
            while (input.readTag() != 0) {
                if (!input.skipField(input.getLastTag())) {
                    break;
                }
            }
            return this;
        }

        @Override
        public Builder mergeFrom(
                byte[] data,
                int offset,
                int length,
                ExtensionRegistryLite extensionRegistry
        ) throws InvalidProtocolBufferException {
            if (length != 0) {
                throw new InvalidProtocolBufferException("Expected an empty legacy message");
            }
            return this;
        }
    }
}
