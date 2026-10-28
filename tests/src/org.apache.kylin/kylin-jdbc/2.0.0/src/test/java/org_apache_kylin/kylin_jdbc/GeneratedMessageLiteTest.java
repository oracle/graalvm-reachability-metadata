/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.IOException;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.CodedOutputStream;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.GeneratedMessageLite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageLiteTest {
    @Test
    void rendersGeneratedLiteFieldsThroughPublicAccessors() {
        PrintableMessage message = new PrintableMessage("quarterly-sales");

        String rendered = message.toString();

        assertThat(rendered).contains("value: \"quarterly-sales\"");
    }

    public static final class PrintableMessage
            extends GeneratedMessageLite<PrintableMessage, PrintableMessage.Builder> {
        private static final PrintableMessage DEFAULT_INSTANCE = new PrintableMessage("");

        private final String value;

        private PrintableMessage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public boolean hasValue() {
            return !value.isEmpty();
        }

        @SuppressWarnings("unused")
        private void setValue(String ignored) {
        }

        @Override
        public void writeTo(CodedOutputStream output) throws IOException {
            output.writeString(1, value);
        }

        @Override
        public int getSerializedSize() {
            return CodedOutputStream.computeStringSize(1, value);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case IS_INITIALIZED:
                    return DEFAULT_INSTANCE;
                case NEW_MUTABLE_INSTANCE:
                    return new PrintableMessage("");
                case NEW_BUILDER:
                    return new Builder();
                case MAKE_IMMUTABLE:
                    return null;
                case VISIT:
                    PrintableMessage other = (PrintableMessage) secondArgument;
                    ((Visitor) firstArgument).visitString(hasValue(), value, other.hasValue(), other.value);
                    return this;
                default:
                    throw new UnsupportedOperationException("Operation is not needed to render the message");
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<PrintableMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
