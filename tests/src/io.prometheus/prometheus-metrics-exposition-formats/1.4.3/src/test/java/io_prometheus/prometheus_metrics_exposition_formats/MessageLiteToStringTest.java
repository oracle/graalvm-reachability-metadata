/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.GeneratedMessageLite;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageLiteToStringTest {
    @Test
    void printsScalarFieldsWithEscapedText() {
        Request message = Request.newBuilder()
                .setRequestCount(42)
                .setRequestName("worker\n\"urgent\"")
                .build();

        String text = message.toString();

        assertThat(text).contains("\nrequest_count: 42", "\nrequest_name: \"worker\\n\\\"urgent\\\"\"");
    }

    @Test
    void omitsDefaultScalarFields() {
        Request message = Request.newBuilder().build();

        assertThat(message.toString()).doesNotContain("request_count:", "request_name:");
        assertThat(Request.newBuilder().setRequestCount(7).build().toString())
                .contains("\nrequest_count: 7")
                .doesNotContain("request_name:");
    }

    /// A consumer-defined lite message with scalar accessors used by the debug printer.
    public static final class Request extends GeneratedMessageLite<Request, Request.Builder> {
        private static final Request DEFAULT_INSTANCE = new Request();

        static {
            registerDefaultInstance(Request.class, DEFAULT_INSTANCE);
        }

        private static final Parser<Request> PARSER = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
        private int requestCount;
        private String requestName = "";

        private Request() {
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        public int getRequestCount() {
            return requestCount;
        }

        private void setRequestCount(int value) {
            requestCount = value;
        }

        public String getRequestName() {
            return requestName;
        }

        private void setRequestName(String value) {
            requestName = value;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            return switch (method) {
                case NEW_MUTABLE_INSTANCE -> new Request();
                case NEW_BUILDER -> new Builder();
                case BUILD_MESSAGE_INFO -> newMessageInfo(DEFAULT_INSTANCE,
                        // Proto3, int32 field #1 and UTF-8 string field #2.
                        "\u0000\u0002\u0000\u0000\u0001\u0002\u0002\u0000\u0000\u0000"
                                + "\u0001\u0004\u0002\u0208",
                        new Object[]{"requestCount", "requestName"});
                case GET_DEFAULT_INSTANCE -> DEFAULT_INSTANCE;
                case GET_PARSER -> PARSER;
                case GET_MEMOIZED_IS_INITIALIZED -> (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED -> null;
            };
        }

        public static final class Builder extends GeneratedMessageLite.Builder<Request, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }

            public Builder setRequestCount(int value) {
                copyOnWrite();
                instance.setRequestCount(value);
                return this;
            }

            public Builder setRequestName(String value) {
                copyOnWrite();
                instance.setRequestName(value);
                return this;
            }
        }
    }
}
