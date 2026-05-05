/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.spi.DefaultConfigProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConfigProviderTest {
    private static final String LOADER_RESOURCE_PROPERTY = "load.loader-configurator-absolute.resource";
    private static final String MONEY_PRECISION_PROPERTY = "org.javamoney.moneta.Money.defaults.precision";
    private static final String OVERRIDE_PROPERTY = "org_javamoney_moneta.moneta_core.DefaultConfigProviderTest.override";

    @Test
    public void constructorLoadsJavamoneyPropertiesResourcesFromClasspath() {
        DefaultConfigProvider configProvider = new DefaultConfigProvider();

        assertThat(configProvider.getProperties())
                .containsEntry(LOADER_RESOURCE_PROPERTY, "/javamoney.properties")
                .containsEntry(MONEY_PRECISION_PROPERTY, "256");
    }

    @Test
    public void getPropertyPrefersSystemPropertyOverride() {
        String previousValue = System.getProperty(OVERRIDE_PROPERTY);
        System.setProperty(OVERRIDE_PROPERTY, "system-value");
        try {
            DefaultConfigProvider configProvider = new DefaultConfigProvider();

            assertThat(configProvider.getProperty(OVERRIDE_PROPERTY)).isEqualTo("system-value");
            assertThat(configProvider.getProperties()).containsEntry(OVERRIDE_PROPERTY, "system-value");
        } finally {
            if (previousValue == null) {
                System.clearProperty(OVERRIDE_PROPERTY);
            } else {
                System.setProperty(OVERRIDE_PROPERTY, previousValue);
            }
        }
    }
}
