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

@SuppressWarnings("deprecation")
public class DefaultConfigProviderTest {
    @Test
    public void constructorLoadsJavamoneyPropertiesFromClasspath() {
        DefaultConfigProvider provider = new DefaultConfigProvider();

        assertThat(provider.getProperty("load.loader-configurator-absolute.type"))
                .isEqualTo("LAZY");
        assertThat(provider.getProperties())
                .containsEntry("load.loader-configurator-absolute.resource", "/javamoney.properties");
    }
}
