/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.logmanager.DefaultConfigurationLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConfigurationLocatorTest {

    @Test
    void findConfigurationLoadsLoggingPropertiesFromThreadContextClassLoader() throws Exception {
        String originalConfiguration = System.getProperty("logging.configuration");
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        try {
            System.clearProperty("logging.configuration");
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            DefaultConfigurationLocator locator = new DefaultConfigurationLocator();
            try (InputStream stream = locator.findConfiguration()) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .contains("test.resource=thread-context-class-loader");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
            if (originalConfiguration == null) {
                System.clearProperty("logging.configuration");
            } else {
                System.setProperty("logging.configuration", originalConfiguration);
            }
        }
    }
}
