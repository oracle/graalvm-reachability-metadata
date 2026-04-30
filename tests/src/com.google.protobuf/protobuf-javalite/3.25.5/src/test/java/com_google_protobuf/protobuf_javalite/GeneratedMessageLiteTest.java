/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.GeneratedMessageLiteAccess;
import com.google.protobuf.Parser;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteTest {
    @Test
    public void getMethodOrDieAndInvokeOrDieCallPublicGeneratedAccessor() {
        ReflectiveToStringMessage message = ReflectiveToStringMessage.createSample();

        Object greeting = GeneratedMessageLiteAccess.invokeNoArgMethodOrDie(
                ReflectiveToStringMessage.class, "getGreeting", message);

        assertEquals("hello", greeting);
    }

    @Test
    public void toStringReflectivelyReadsSingularRepeatedAndMapAccessors() {
        ReflectiveToStringMessage message = ReflectiveToStringMessage.createSample();

        String printedMessage = message.toString();

        assertTrue(printedMessage.contains("greeting: \"hello\""));
        assertTrue(printedMessage.contains("alias: \"first\""));
        assertTrue(printedMessage.contains("alias: \"second\""));
        assertTrue(printedMessage.contains("attribute {"));
        assertTrue(printedMessage.contains("key: \"priority\""));
        assertTrue(printedMessage.contains("value: 7"));
    }

    public static final class ReflectiveToStringMessage
            extends GeneratedMessageLite<
                    ReflectiveToStringMessage, ReflectiveToStringMessageBuilder> {
        private static final ReflectiveToStringMessage DEFAULT_INSTANCE =
                new ReflectiveToStringMessage();
        private static volatile Parser<ReflectiveToStringMessage> parser;

        static {
            registerDefaultInstance(ReflectiveToStringMessage.class, DEFAULT_INSTANCE);
        }

        private String greeting = "";
        private List<String> aliases = Arrays.asList();
        private Map<String, Integer> attributes = new LinkedHashMap<>();

        private ReflectiveToStringMessage() {
        }

        private static ReflectiveToStringMessage createSample() {
            ReflectiveToStringMessage message = new ReflectiveToStringMessage();
            message.greeting = "hello";
            message.aliases = Arrays.asList("first", "second");
            message.attributes = new LinkedHashMap<>();
            message.attributes.put("priority", 7);
            return message;
        }

        public String getGreeting() {
            return greeting;
        }

        public boolean hasGreeting() {
            return !greeting.isEmpty();
        }

        @SuppressWarnings("unused")
        private void setGreeting(String greeting) {
            this.greeting = greeting;
        }

        public List<String> getAliasList() {
            return aliases;
        }

        public Map<String, Integer> getAttributeMap() {
            return attributes;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ReflectiveToStringMessage();
                case NEW_BUILDER:
                    return new ReflectiveToStringMessageBuilder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<ReflectiveToStringMessage> result = parser;
                    if (result == null) {
                        synchronized (ReflectiveToStringMessage.class) {
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

    public static final class ReflectiveToStringMessageBuilder
            extends GeneratedMessageLite.Builder<
                    ReflectiveToStringMessage, ReflectiveToStringMessageBuilder> {
        private ReflectiveToStringMessageBuilder() {
            super(ReflectiveToStringMessage.DEFAULT_INSTANCE);
        }
    }
}
