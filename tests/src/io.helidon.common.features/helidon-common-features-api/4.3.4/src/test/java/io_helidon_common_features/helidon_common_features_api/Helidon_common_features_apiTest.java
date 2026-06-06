/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common_features.helidon_common_features_api;

import java.lang.annotation.Annotation;

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Helidon_common_features_apiTest {
    @Test
    void helidonFlavorExposesStableEnumContract() {
        assertThat(HelidonFlavor.values()).containsExactly(HelidonFlavor.SE, HelidonFlavor.MP);
        assertThat(HelidonFlavor.valueOf("SE")).isSameAs(HelidonFlavor.SE);
        assertThat(HelidonFlavor.valueOf("MP")).isSameAs(HelidonFlavor.MP);
        assertThat(HelidonFlavor.SE.name()).isEqualTo("SE");
        assertThat(HelidonFlavor.MP.name()).isEqualTo("MP");
        assertThat(HelidonFlavor.SE.ordinal()).isZero();
        assertThat(HelidonFlavor.MP.ordinal()).isEqualTo(1);
    }

    @Test
    void helidonFlavorMapsMetadataFlavorValues() {
        assertThat(HelidonFlavor.map(io.helidon.common.features.metadata.Flavor.SE))
                .isSameAs(HelidonFlavor.SE);
        assertThat(HelidonFlavor.map(io.helidon.common.features.metadata.Flavor.MP))
                .isSameAs(HelidonFlavor.MP);
    }

    @Test
    void helidonFlavorRejectsUnknownNames() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> HelidonFlavor.valueOf("UNKNOWN"));
    }

    @Test
    void featureDescriptorAnnotationsExposeConfiguredValues() {
        Features.Name name = new FeatureName("Database Client");
        Features.Description description = new FeatureDescription("Connects to databases");
        Features.Since since = new FeatureSince("4.0.0");
        Features.Path path = new FeaturePath("data", "jdbc");
        Features.Flavor flavor = new FeatureFlavor(HelidonFlavor.SE);
        Features.InvalidFlavor invalidFlavor = new FeatureInvalidFlavor(HelidonFlavor.MP);

        assertThat(name.value()).isEqualTo("Database Client");
        assertThat(description.value()).isEqualTo("Connects to databases");
        assertThat(since.value()).isEqualTo("4.0.0");
        assertThat(path.value()).containsExactly("data", "jdbc");
        assertThat(flavor.value()).containsExactly(HelidonFlavor.SE);
        assertThat(invalidFlavor.value()).containsExactly(HelidonFlavor.MP);
    }

    @Test
    void aotDescriptorAnnotationExposesSupportFlagAndDescription() {
        Features.Aot supported = new FeatureAot(true, "Supported in native image");
        Features.Aot limited = new FeatureAot(false, "Requires runtime bytecode generation");

        assertThat(supported.value()).isTrue();
        assertThat(supported.description()).isEqualTo("Supported in native image");
        assertThat(limited.value()).isFalse();
        assertThat(limited.description()).isEqualTo("Requires runtime bytecode generation");
    }

    @Test
    void markerAnnotationsExposeTheirAnnotationTypes() {
        Features.Preview preview = new FeaturePreview();
        Features.Incubating incubating = new FeatureIncubating();

        assertThat(preview.annotationType()).isEqualTo(Features.Preview.class);
        assertThat(incubating.annotationType()).isEqualTo(Features.Incubating.class);
    }

    private record FeatureName(String value) implements Features.Name {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Name.class;
        }
    }

    private record FeatureDescription(String value) implements Features.Description {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Description.class;
        }
    }

    private record FeatureSince(String value) implements Features.Since {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Since.class;
        }
    }

    private static final class FeaturePath implements Features.Path {
        private final String[] value;

        private FeaturePath(String... value) {
            this.value = value.clone();
        }

        @Override
        public String[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Path.class;
        }
    }

    private static final class FeatureFlavor implements Features.Flavor {
        private final HelidonFlavor[] value;

        private FeatureFlavor(HelidonFlavor... value) {
            this.value = value.clone();
        }

        @Override
        public HelidonFlavor[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Flavor.class;
        }
    }

    private static final class FeatureInvalidFlavor implements Features.InvalidFlavor {
        private final HelidonFlavor[] value;

        private FeatureInvalidFlavor(HelidonFlavor... value) {
            this.value = value.clone();
        }

        @Override
        public HelidonFlavor[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.InvalidFlavor.class;
        }
    }

    private record FeatureAot(boolean value, String description) implements Features.Aot {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Aot.class;
        }
    }

    private static final class FeaturePreview implements Features.Preview {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Preview.class;
        }
    }

    private static final class FeatureIncubating implements Features.Incubating {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Features.Incubating.class;
        }
    }
}
