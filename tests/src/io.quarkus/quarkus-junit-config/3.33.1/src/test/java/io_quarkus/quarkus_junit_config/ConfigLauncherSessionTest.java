/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ConfigLauncherSessionTest {
    @Test
    void configLauncherSessionIsRegisteredAsLauncherSessionListener() throws IOException {
        String serviceFile = "META-INF/services/org.junit.platform.launcher.LauncherSessionListener";

        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(serviceFile)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("io.quarkus.test.config.ConfigLauncherSession");
        }
    }
}
