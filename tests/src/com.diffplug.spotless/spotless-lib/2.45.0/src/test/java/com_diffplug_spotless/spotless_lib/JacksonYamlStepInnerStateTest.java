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
import com.diffplug.spotless.yaml.JacksonYamlConfig;
import com.diffplug.spotless.yaml.JacksonYamlStep;

public class JacksonYamlStepInnerStateTest {
    private static final String INPUT = "name: spotless\nactive: true\n";

    @Test
    void createsJacksonYamlFormatterWithConfiguredOptions() throws Exception {
        final JacksonYamlConfig config = configuredYamlFormatter();
        final Map<String, Boolean> featureToggles = config.getFeatureToToggle();
        final Map<String, Boolean> yamlFeatureToggles = config.getYamlFeatureToToggle();
        final FormatterStep step = JacksonYamlStep.create(config, JacksonYamlStep.defaultVersion(),
                new FixtureProvisioner());

        assertThat(step.format(INPUT, Formatter.NO_FILE_SENTINEL))
                .isEqualTo("jackson-yaml:" + featureToggles + ":" + yamlFeatureToggles + ":" + INPUT);
    }

    private static JacksonYamlConfig configuredYamlFormatter() {
        final Map<String, Boolean> featureToggles = new LinkedHashMap<>();
        featureToggles.put("INDENT_OUTPUT", true);
        featureToggles.put("ORDER_MAP_ENTRIES_BY_KEYS", false);

        final Map<String, Boolean> yamlFeatureToggles = new LinkedHashMap<>();
        yamlFeatureToggles.put("MINIMIZE_QUOTES", true);
        yamlFeatureToggles.put("SPLIT_LINES", false);

        final JacksonYamlConfig config = new JacksonYamlConfig();
        config.setFeatureToToggle(featureToggles);
        config.setYamlFeatureToToggle(yamlFeatureToggles);
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
                try (InputStream inputStream = resource("jackson-yaml-fixtures/current.classpath");
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
                throw new IllegalStateException("Unable to extract Jackson YAML fixture jars", e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("jackson-yaml-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = JacksonYamlStepInnerStateTest.class.getClassLoader()
                    .getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
