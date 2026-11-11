/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.CodedInputStream;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.DescriptorProtos.FeatureSet;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.ExtensionRegistry;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.GeneratedMessageLite;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.JavaFeaturesProto;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.JavaFeaturesProto.JavaFeatures;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionSchemasTest {
    @Test
    void roundTripsFullRuntimeExtensionsInsideLiteMessage() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        JavaFeaturesProto.registerAllExtensions(registry);
        FeatureSet features = FeatureSet.newBuilder()
                .setFieldPresence(FeatureSet.FieldPresence.EXPLICIT)
                .setExtension(JavaFeaturesProto.java_, JavaFeatures.newBuilder()
                        .setLegacyClosedEnum(true)
                        .setUtf8Validation(JavaFeatures.Utf8Validation.VERIFY)
                        .build())
                .build();
        FeatureEnvelope original = FeatureEnvelope.newBuilder().setFeatures(features).build();
        byte[] bytes = original.toByteArray();
        CodedInputStream input = CodedInputStream.newInstance(bytes);

        FeatureEnvelope decoded = original.getParserForType().parseFrom(input, registry);

        assertThat(input.isAtEnd()).isTrue();
        assertThat(decoded.getFeatures()).isEqualTo(features);
        assertThat(decoded.getFeatures().getFieldPresence()).isEqualTo(FeatureSet.FieldPresence.EXPLICIT);
        assertThat(decoded.getFeatures().hasExtension(JavaFeaturesProto.java_)).isTrue();
        assertThat(decoded.getFeatures().getExtension(JavaFeaturesProto.java_).getLegacyClosedEnum()).isTrue();
        assertThat(decoded.getFeatures().getExtension(JavaFeaturesProto.java_).getUtf8Validation())
                .isEqualTo(JavaFeatures.Utf8Validation.VERIFY);
        assertThat(decoded.toByteArray()).isEqualTo(bytes);
    }

    /// A lite envelope carrying a full-runtime descriptor message.
    public static final class FeatureEnvelope
            extends GeneratedMessageLite<FeatureEnvelope, FeatureEnvelope.Builder> {
        private static final FeatureEnvelope DEFAULT_INSTANCE = new FeatureEnvelope();

        static {
            registerDefaultInstance(FeatureEnvelope.class, DEFAULT_INSTANCE);
        }

        private static final Parser<FeatureEnvelope> PARSER = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
        private FeatureSet features;

        private FeatureEnvelope() {
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        public FeatureSet getFeatures() {
            return features == null ? FeatureSet.getDefaultInstance() : features;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            return switch (method) {
                case NEW_MUTABLE_INSTANCE -> new FeatureEnvelope();
                case NEW_BUILDER -> new Builder();
                case BUILD_MESSAGE_INFO -> newMessageInfo(DEFAULT_INSTANCE,
                        // Proto3, one message field numbered 1.
                        "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000"
                                + "\u0001\u0009",
                        new Object[]{"features"});
                case GET_DEFAULT_INSTANCE -> DEFAULT_INSTANCE;
                case GET_PARSER -> PARSER;
                case GET_MEMOIZED_IS_INITIALIZED -> (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED -> null;
            };
        }

        public static final class Builder extends GeneratedMessageLite.Builder<FeatureEnvelope, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }

            public Builder setFeatures(FeatureSet features) {
                copyOnWrite();
                instance.features = features;
                return this;
            }
        }
    }
}
