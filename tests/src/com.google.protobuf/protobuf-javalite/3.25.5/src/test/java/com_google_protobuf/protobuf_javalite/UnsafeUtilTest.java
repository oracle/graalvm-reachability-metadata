/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.Parser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    private static final String SAMPLE_TEXT = "protobuf unsafe utility coverage \u03c0 \ud83d\ude80";

    @Test
    public void protobufOperationsExerciseUnsafeUtilDynamicAccess() {
        byte[] encoded = SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8);
        ByteString bytes = ByteString.copyFrom(encoded);

        ByteString changedBytes = ByteString.copyFromUtf8("protobuf unsafe utility coverage \u03c0 \ud83e\uddea");

        assertTrue(bytes.isValidUtf8());
        assertEquals(SAMPLE_TEXT, bytes.toStringUtf8());
        assertEquals(bytes.size(), changedBytes.size());
        assertNotEquals(bytes, changedBytes);
        assertEquals(0, FallbackMessage.defaultInstance().getSerializedSize());
    }
}

final class FallbackMessage extends GeneratedMessageLite<FallbackMessage, FallbackMessageBuilder> {
    private static final FallbackMessage DEFAULT_INSTANCE = new FallbackMessage();
    private static volatile Parser<FallbackMessage> parser;

    private FallbackMessage() {
    }

    static FallbackMessage defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new FallbackMessage();
            case NEW_BUILDER:
                return new FallbackMessageBuilder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<FallbackMessage> result = parser;
                if (result == null) {
                    synchronized (FallbackMessage.class) {
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

final class FallbackMessageBuilder
        extends GeneratedMessageLite.Builder<FallbackMessage, FallbackMessageBuilder> {
    FallbackMessageBuilder() {
        super(FallbackMessage.defaultInstance());
    }
}
