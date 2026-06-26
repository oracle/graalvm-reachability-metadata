/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common_features.helidon_common_features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.helidon.common.features.HelidonFeatures;

import org.junit.jupiter.api.Test;

public class FeatureCatalogTest {
    @Test
    void discoversFeatureMetadataResourcesFromClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThat(classLoader).isNotNull();
        assertThatCode(() -> HelidonFeatures.nativeBuildTime(classLoader))
                .doesNotThrowAnyException();
    }
}
