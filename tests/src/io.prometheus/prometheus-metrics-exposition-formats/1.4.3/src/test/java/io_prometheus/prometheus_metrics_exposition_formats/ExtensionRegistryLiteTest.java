/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.DescriptorProtos.FeatureSet;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.ExtensionRegistry;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.ExtensionRegistryLite;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.JavaFeaturesProto;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.JavaFeaturesProto.JavaFeatures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionRegistryLiteTest {
    @Test
    void registersFullExtensionThroughLiteInterface() throws Exception {
        ExtensionRegistry fullRegistry = ExtensionRegistry.newInstance();
        assertThat(fullRegistry.findImmutableExtensionByNumber(
                FeatureSet.getDescriptor(), JavaFeaturesProto.JAVA_FIELD_NUMBER)).isNull();
        ExtensionRegistryLite registry = fullRegistry;
        JavaFeaturesProto.registerAllExtensions(registry);
        FeatureSet original = FeatureSet.newBuilder()
                .setExtension(JavaFeaturesProto.java_, JavaFeatures.newBuilder()
                        .setUtf8Validation(JavaFeatures.Utf8Validation.VERIFY).build())
                .build();

        FeatureSet decoded = FeatureSet.parseFrom(original.toByteString(), registry.getUnmodifiable());

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.getExtension(JavaFeaturesProto.java_).getUtf8Validation())
                .isEqualTo(JavaFeatures.Utf8Validation.VERIFY);
    }
}
