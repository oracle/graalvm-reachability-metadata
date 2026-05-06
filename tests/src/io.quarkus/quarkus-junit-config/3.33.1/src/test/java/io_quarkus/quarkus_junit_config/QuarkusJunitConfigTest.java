/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.quarkus.test.config.TestConfigCustomizer;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class QuarkusJunitConfigTest {
    @Test
    void testConfigCustomizerSetsBuildToolAwareLogFileDefault() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withCustomizers(new TestConfigCustomizer())
                .build();

        assertThat(config.getValue("quarkus.log.file.path", String.class))
                .isEqualTo(expectedLogFilePath());
    }

    @Test
    void explicitLogFileConfigurationOverridesCustomizerDefault() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withDefaultValue("quarkus.log.file.path", "custom.log")
                .withCustomizers(new TestConfigCustomizer())
                .build();

        assertThat(config.getValue("quarkus.log.file.path", String.class)).isEqualTo("custom.log");
    }

    private static String expectedLogFilePath() {
        String buildDirectory = Files.isDirectory(Paths.get("build")) ? "build" : "target";
        return buildDirectory + File.separator + "quarkus.log";
    }
}
