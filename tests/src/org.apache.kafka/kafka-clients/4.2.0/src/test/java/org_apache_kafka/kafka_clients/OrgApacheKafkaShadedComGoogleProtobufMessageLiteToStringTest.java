/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufMessageLiteToStringTest {

    @Test
    void generatedMessageLiteToStringPrintsDeclaredAccessors() {
        PrintableLiteMessage message = new PrintableLiteMessage("native-image");

        String printed = message.toString();

        assertThat(printed)
                .contains("# ")
                .contains("value: \"native-image\"");
    }

    public static final class PrintableLiteMessage
            extends GeneratedMessageLite<PrintableLiteMessage, PrintableLiteMessage.Builder> {
        private static final PrintableLiteMessage DEFAULT_INSTANCE = new PrintableLiteMessage("");

        private String value;

        private PrintableLiteMessage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public boolean hasValue() {
            return !value.isEmpty();
        }

        @SuppressWarnings("unused")
        private void setValue(String value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
            return switch (method) {
                case GET_MEMOIZED_IS_INITIALIZED -> (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED -> null;
                case BUILD_MESSAGE_INFO -> null;
                case NEW_MUTABLE_INSTANCE -> new PrintableLiteMessage("");
                case NEW_BUILDER -> new Builder();
                case GET_DEFAULT_INSTANCE -> DEFAULT_INSTANCE;
                case GET_PARSER -> null;
            };
        }

        public static final class Builder extends GeneratedMessageLite.Builder<PrintableLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
