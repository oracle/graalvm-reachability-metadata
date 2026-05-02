/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.internal.loader.DefaultLoaderService;
import org.javamoney.moneta.spi.LoaderService;
import org.javamoney.moneta.spi.LoaderService.UpdatePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderConfiguratorTest {
    private static final String RESOURCE_ID = "loader-configurator-absolute";

    @Test
    public void loadRegistersResourceFoundThroughOwningClass() {
        LoaderService loaderService = new DefaultLoaderService();

        assertThat(loaderService.isResourceRegistered(RESOURCE_ID)).isTrue();
        assertThat(loaderService.getUpdatePolicy(RESOURCE_ID)).isEqualTo(UpdatePolicy.LAZY);
        assertThat(loaderService.getUpdateConfiguration(RESOURCE_ID))
                .containsEntry("resource", "/javamoney.properties")
                .containsEntry("type", "LAZY");
    }
}
