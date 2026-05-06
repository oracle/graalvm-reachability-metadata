/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit_config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.quarkus.test.config.TestValueRegistryConfigSource;

public class TestValueRegistryConfigSourceTest {
    @Test
    void configNamespaceIsStableAndPrivateToTheValueRegistrySource() {
        ExtensionContext.Namespace configNamespace = TestValueRegistryConfigSource.CONFIG;

        assertThat(configNamespace).isSameAs(TestValueRegistryConfigSource.CONFIG);
        assertThat(configNamespace).isNotEqualTo(ExtensionContext.Namespace.GLOBAL);
    }

    @Test
    void configNamespaceCanBeExtendedForAdditionalStoreEntries() {
        ExtensionContext.Namespace configNamespace = TestValueRegistryConfigSource.CONFIG;

        assertThat(configNamespace.append("additional-entry"))
                .isEqualTo(configNamespace.append("additional-entry"))
                .isNotEqualTo(configNamespace);
    }
}
