/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigDataEnvironmentPostProcessorTest {
    @TempDir
    Path directory;

    @Test
    void loadsExplicitConfigLocationsAndIgnoresAnAbsentLocationWhenConfigured() throws Exception {
        Path application = this.directory.resolve("orders.properties");
        Files.writeString(application, "orders.destination=warehouse\n");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("deployment", Map.of(
                "spring.config.location", application.toUri() + "," + this.directory.resolve("absent.properties").toUri(),
                "spring.config.on-not-found", "ignore")));

        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        assertThat(environment.getProperty("orders.destination")).isEqualTo("warehouse");
        PropertySource<?> configSource = environment.getPropertySources().stream()
                .filter(source -> "warehouse".equals(source.getProperty("orders.destination")))
                .findFirst().orElseThrow();
        Origin origin = OriginLookup.getOrigin(configSource, "orders.destination");
        assertThat(origin).isNotNull();
        assertThat(origin.toString()).contains("orders.properties", "1:");
    }
}
