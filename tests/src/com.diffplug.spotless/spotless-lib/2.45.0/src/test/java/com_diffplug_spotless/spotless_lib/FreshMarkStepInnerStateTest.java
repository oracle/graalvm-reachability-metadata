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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.markdown.FreshMarkStep;

public class FreshMarkStepInnerStateTest {
    private static final String INPUT = "# ${project}";

    private final Provisioner provisioner = new FixtureProvisioner();

    @Test
    void createsFreshMarkFormatterFromProvisionedClasspath() throws Exception {
        final FormatterStep step = FreshMarkStep.create(() -> Map.of("project", "spotless"), provisioner);

        assertThat(step.format(INPUT, Formatter.NO_FILE_SENTINEL)).isEqualTo("freshmark:spotless:" + INPUT);
    }

    private static final class FixtureProvisioner implements Provisioner {
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return extractFixtureJars();
        }

        private static Set<File> extractFixtureJars() {
            try {
                final Set<File> files = new LinkedHashSet<>();
                try (InputStream inputStream = resource("freshmark-fixtures/current.classpath");
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
                throw new IllegalStateException("Unable to extract FreshMark fixture jars", e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("freshmark-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = FreshMarkStepInnerStateTest.class.getClassLoader()
                    .getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
