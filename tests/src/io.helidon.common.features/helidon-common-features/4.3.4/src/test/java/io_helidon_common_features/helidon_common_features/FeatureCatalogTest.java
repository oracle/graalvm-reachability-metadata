/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common_features.helidon_common_features;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.helidon.common.features.HelidonFeatures;

import org.junit.jupiter.api.Test;

public class FeatureCatalogTest {
    @Test
    void nativeBuildTimeDiscoversFeaturesFromClasspath() {
        final ClassLoader classLoader = FeatureCatalogTest.class.getClassLoader();

        assertDoesNotThrow(() -> HelidonFeatures.nativeBuildTime(classLoader));
    }
}
