/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.json.JacksonJsonConfig;
import com.diffplug.spotless.json.JacksonJsonStep;

public class JacksonJsonStepInnerStateTest {
    private static final String INPUT = "{\"name\":\"spotless\",\"active\":true}";

    @Test
    void createsJacksonJsonFormatterWithConfiguredOptions() throws Exception {
        final JacksonJsonConfig config = configuredJsonFormatter();
        final Map<String, Boolean> featureToggles = config.getFeatureToToggle();
        final Map<String, Boolean> jsonFeatureToggles = config.getJsonFeatureToToggle();
        final FormatterStep step = JacksonJsonStep.create(config, "current", new FixtureProvisioner());

        assertThat(step.format(INPUT, Formatter.NO_FILE_SENTINEL))
                .isEqualTo("jackson-json:" + featureToggles + ":" + jsonFeatureToggles + ":true:" + INPUT);
    }

    private static JacksonJsonConfig configuredJsonFormatter() {
        final Map<String, Boolean> featureToggles = new LinkedHashMap<>();
        featureToggles.put("INDENT_OUTPUT", true);
        featureToggles.put("ORDER_MAP_ENTRIES_BY_KEYS", false);

        final Map<String, Boolean> jsonFeatureToggles = new LinkedHashMap<>();
        jsonFeatureToggles.put("WRITE_NUMBERS_AS_STRINGS", true);

        final JacksonJsonConfig config = new JacksonJsonConfig();
        config.setFeatureToToggle(featureToggles);
        config.setJsonFeatureToToggle(jsonFeatureToggles);
        config.setSpaceBeforeSeparator(true);
        return config;
    }

    private static final class FixtureProvisioner implements Provisioner {
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return extractFixtureJars();
        }

        private static Set<File> extractFixtureJars() {
            try {
                final Set<File> files = new LinkedHashSet<>();
                try (InputStream inputStream = resource("jackson-json-fixtures/current.classpath");
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String jarResource;
                    while ((jarResource = reader.readLine()) != null) {
                        if (!jarResource.isBlank()) {
                            files.add(extractResource(jarResource).toFile());
                        }
                    }
                }
                return files;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to extract Jackson JSON fixture jars", e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("jackson-json-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = JacksonJsonStepInnerStateTest.class.getClassLoader()
                    .getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
