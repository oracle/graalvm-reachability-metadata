/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteInnerSerializedFormTest {
    @Test
    void readResolveLoadsClassByNameAndUsesDefaultInstanceField() throws Exception {
        Object resolved = serializeFormAndReadBackWithClassNameLookup(
                CurrentStyleMessage.defaultInstance());

        assertThat(resolved).isInstanceOf(CurrentStyleMessage.class);
        assertThat(((MessageLite) resolved).getSerializedSize()).isZero();
    }

    @Test
    void readResolveLoadsClassByNameAndFallsBackToLegacyDefaultInstanceField() throws Exception {
        Object resolved = serializeFormAndReadBackWithClassNameLookup(
                LegacyStyleMessage.defaultInstance());

        assertThat(resolved).isInstanceOf(LegacyStyleMessage.class);
        assertThat(((MessageLite) resolved).getSerializedSize()).isZero();
    }

    private static Object serializeFormAndReadBackWithClassNameLookup(MessageLite message)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ClassNullingObjectOutputStream output = new ClassNullingObjectOutputStream(bytes)) {
            output.writeObject(CurrentStyleMessage.serializedFormOf(message));
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return input.readObject();
        }
    }

    private static class ClassNullingObjectOutputStream extends ObjectOutputStream {
        ClassNullingObjectOutputStream(OutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object == CurrentStyleMessage.class || object == LegacyStyleMessage.class) {
                return null;
            }
            return super.replaceObject(object);
        }
    }

    public static final class CurrentStyleMessage
            extends GeneratedMessageLite<CurrentStyleMessage, CurrentStyleMessage.Builder> {
        private static final CurrentStyleMessage DEFAULT_INSTANCE = new CurrentStyleMessage();
        private static volatile Parser<CurrentStyleMessage> PARSER;

        static {
            registerDefaultInstance(CurrentStyleMessage.class, DEFAULT_INSTANCE);
        }

        private CurrentStyleMessage() {
        }

        static CurrentStyleMessage defaultInstance() {
            return DEFAULT_INSTANCE;
        }

        static Object serializedFormOf(MessageLite message) {
            return SerializedForm.of(message);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new CurrentStyleMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<CurrentStyleMessage> localParser = PARSER;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        PARSER = localParser;
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
                extends GeneratedMessageLite.Builder<CurrentStyleMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class LegacyStyleMessage
            extends GeneratedMessageLite<LegacyStyleMessage, LegacyStyleMessage.Builder> {
        @SuppressWarnings("checkstyle:ConstantName")
        private static final LegacyStyleMessage defaultInstance = new LegacyStyleMessage();
        private static volatile Parser<LegacyStyleMessage> PARSER;

        static {
            registerDefaultInstance(LegacyStyleMessage.class, defaultInstance);
        }

        private LegacyStyleMessage() {
        }

        static LegacyStyleMessage defaultInstance() {
            return defaultInstance;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacyStyleMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_PARSER:
                    Parser<LegacyStyleMessage> localParser = PARSER;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(defaultInstance);
                        PARSER = localParser;
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
                extends GeneratedMessageLite.Builder<LegacyStyleMessage, Builder> {
            private Builder() {
                super(defaultInstance);
            }
        }
    }
}
