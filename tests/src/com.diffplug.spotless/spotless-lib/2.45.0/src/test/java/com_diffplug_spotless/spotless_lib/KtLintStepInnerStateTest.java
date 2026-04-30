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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.FileSignature;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.kotlin.KtLintStep;

public class KtLintStepInnerStateTest {
    private static final String KTLINT_VERSION = "1.2.3";
    private static final String CUSTOM_RULESET = "com.example.ktlint:ruleset:1.0";
    private static final String SOURCE = "fun main() { println(\"hi\") }";

    private final FixtureProvisioner provisioner = new FixtureProvisioner();

    @Test
    void createsFormatterFromProvisionedKtlintGlueClass() throws Exception {
        final Path editorConfig = Files.createTempFile("ktlint-editor-config-", ".editorconfig");
        Files.writeString(editorConfig, "root = true\n", StandardCharsets.UTF_8);
        editorConfig.toFile().deleteOnExit();
        final FileSignature editorConfigSignature = FileSignature.signAsList(editorConfig.toFile());
        final Map<String, Object> editorConfigOverride = new LinkedHashMap<>();
        editorConfigOverride.put("ktlint_standard_final-newline", "disabled");
        final FormatterStep step = KtLintStep.create(
                KTLINT_VERSION,
                provisioner,
                editorConfigSignature,
                editorConfigOverride,
                List.of(CUSTOM_RULESET));
        final Path sourceFile = Files.createTempFile("ktlint-source-", ".kt");
        sourceFile.toFile().deleteOnExit();

        assertThat(step.format(SOURCE, sourceFile.toFile())).isEqualTo("ktlint:"
                + KTLINT_VERSION
                + ":"
                + editorConfig.getFileName()
                + ":{ktlint_standard_final-newline=disabled}:"
                + sourceFile.getFileName()
                + ":"
                + SOURCE);
        assertThat(provisioner.mavenCoordinates).contains(
                "com.pinterest.ktlint:ktlint-cli:" + KTLINT_VERSION,
                CUSTOM_RULESET);
    }

    private static final class FixtureProvisioner implements Provisioner {
        private Set<String> mavenCoordinates = Set.of();

        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            this.mavenCoordinates = new LinkedHashSet<>(mavenCoordinates);
            return extractFixtureJars();
        }

        private static Set<File> extractFixtureJars() {
            try {
                final Set<File> files = new LinkedHashSet<>();
                try (InputStream inputStream = resource("ktlint-fixtures/current.classpath");
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
                throw new IllegalStateException("Unable to extract ktlint fixture jars", e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("ktlint-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = KtLintStepInnerStateTest.class.getClassLoader()
                    .getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
