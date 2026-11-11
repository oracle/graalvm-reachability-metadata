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

public class MessageSchemaTest {
    @Test
    void roundTripsLiteMessageFieldsAndPreservesBuiltInstances() throws Exception {
        Sample.Builder builder = Sample.newBuilder().setCount(150).setLabel("worker");
        Sample original = builder.build();
        Sample updated = builder.setCount(300).build();
        byte[] bytes = original.toByteArray();
        Sample decoded = original.getParserForType().parseFrom(bytes);

        assertThat(bytes).isEqualTo(new byte[]{8, (byte) 150, 1, 18, 6, 119, 111, 114, 107, 101, 114});
        assertThat(decoded.getCount()).isEqualTo(150);
        assertThat(decoded.getLabel()).isEqualTo("worker");
        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.getSerializedSize()).isEqualTo(bytes.length);
        assertThat(original.getCount()).isEqualTo(150);
        assertThat(updated.getCount()).isEqualTo(300);
        assertThat(updated.getLabel()).isEqualTo("worker");
    }

    /// A minimal lite message with an int64 field and a UTF-8 string field.
    public static final class Sample extends GeneratedMessageLite<Sample, Sample.Builder> {
        private static final Sample DEFAULT_INSTANCE = new Sample();

        static {
            registerDefaultInstance(Sample.class, DEFAULT_INSTANCE);
        }

        private static final Parser<Sample> PARSER = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);

        private long count;
        private String label = "";

        private Sample() {
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        public long getCount() {
            return count;
        }

        public String getLabel() {
            return label;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            return switch (method) {
                case NEW_MUTABLE_INSTANCE -> new Sample();
                case NEW_BUILDER -> new Builder();
                case BUILD_MESSAGE_INFO -> newMessageInfo(DEFAULT_INSTANCE,
                        // Proto3, two scalar fields: int64 #1, UTF-8 string #2.
                        "\u0000\u0002\u0000\u0000\u0001\u0002\u0002\u0000\u0000\u0000"
                                + "\u0001\u0002\u0002\u0208",
                        new Object[]{"count", "label"});
                case GET_DEFAULT_INSTANCE -> DEFAULT_INSTANCE;
                case GET_PARSER -> PARSER;
                case GET_MEMOIZED_IS_INITIALIZED -> (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED -> null;
            };
        }

        public static final class Builder extends GeneratedMessageLite.Builder<Sample, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }

            public Builder setCount(long count) {
                copyOnWrite();
                instance.count = count;
                return this;
            }

            public Builder setLabel(String label) {
                copyOnWrite();
                instance.label = label;
                return this;
            }
        }
    }
}
