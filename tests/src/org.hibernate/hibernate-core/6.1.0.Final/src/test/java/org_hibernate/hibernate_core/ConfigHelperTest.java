/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.hibernate.internal.util.ConfigHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigHelperTest {

    @Test
    public void findsConfigurationResourcesUsingSupportedClassLoaderLookup() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            URL missingResource = ConfigHelper.findAsResource("missing-config-helper-resource");
            assertThat(missingResource).isNull();

            try (InputStream resource = ConfigHelper.getResourceAsStream("logback.xml")) {
                assertThat(resource.readAllBytes()).isNotEmpty();
            }

            try (InputStream resource = ConfigHelper.getUserResourceAsStream("/logback.xml")) {
                assertThat(resource.readAllBytes()).isNotEmpty();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
