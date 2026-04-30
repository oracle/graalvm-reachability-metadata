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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.java.CleanthatJavaStep;

public class CleanthatJavaStepInnerJavaRefactorerStateTest {
    private static final String SOURCE = "class Example { void run() { String value = \"spotless\"; } }";

    private final FixtureProvisioner provisioner = new FixtureProvisioner();

    @Test
    void createsCleanthatFormatterFromProvisionedGlueClass() throws Exception {
        final List<String> included = List.of("SafeAndConsensual", "SafeButNotConsensual");
        final List<String> excluded = List.of("ObsoleteIfRatherThanElseAndIf");
        final FormatterStep step = CleanthatJavaStep.create(
                CleanthatJavaStep.defaultGroupArtifact(),
                CleanthatJavaStep.defaultVersion(),
                "17",
                included,
                excluded,
                true,
                provisioner);

        assertThat(step.format(SOURCE, Formatter.NO_FILE_SENTINEL)).isEqualTo("cleanthat:17:"
                + included
                + ":"
                + excluded
                + ":true:"
                + SOURCE);
        assertThat(provisioner.mavenCoordinates()).containsExactly(
                CleanthatJavaStep.defaultGroupArtifact() + ":" + CleanthatJavaStep.defaultVersion());
    }

    private static final class FixtureProvisioner implements Provisioner {
        private Collection<String> mavenCoordinates = Set.of();

        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            this.mavenCoordinates = mavenCoordinates;
            return extractFixtureJars();
        }

        private Collection<String> mavenCoordinates() {
            return mavenCoordinates;
        }

        private static Set<File> extractFixtureJars() {
            try {
                final Set<File> files = new LinkedHashSet<>();
                try (InputStream inputStream = resource("cleanthat-fixtures/current.classpath");
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
                throw new IllegalStateException("Unable to extract Cleanthat fixture jars", e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("cleanthat-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = CleanthatJavaStepInnerJavaRefactorerStateTest.class.getClassLoader()
                    .getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
