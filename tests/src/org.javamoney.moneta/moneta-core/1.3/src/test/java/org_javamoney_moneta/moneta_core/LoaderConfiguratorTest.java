/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.javamoney.moneta.internal.loader.DefaultLoaderService;
import org.javamoney.moneta.spi.LoaderService.UpdatePolicy;
import org.junit.jupiter.api.Test;

public class LoaderConfiguratorTest {
    @Test
    void loadsFallbackResourcesConfiguredInJavamoneyProperties() throws IOException {
        DefaultLoaderService loaderService = new DefaultLoaderService();

        assertThat(loaderService.isResourceRegistered("contextLoaderProbe")).isTrue();
        assertThat(loaderService.getUpdatePolicy("contextLoaderProbe")).isEqualTo(UpdatePolicy.NEVER);
        assertThat(readData(loaderService, "contextLoaderProbe")).contains("context loader resource");

        assertThat(loaderService.isResourceRegistered("classLoaderProbe")).isTrue();
        assertThat(loaderService.getUpdatePolicy("classLoaderProbe")).isEqualTo(UpdatePolicy.NEVER);
        assertThat(readData(loaderService, "classLoaderProbe")).contains("class loader resource");
    }

    private static String readData(DefaultLoaderService loaderService, String resourceId) throws IOException {
        try (InputStream inputStream = loaderService.getData(resourceId)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
