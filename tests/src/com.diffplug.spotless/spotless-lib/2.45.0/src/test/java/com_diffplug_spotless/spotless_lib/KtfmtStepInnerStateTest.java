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
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.kotlin.KtfmtStep;
import com.diffplug.spotless.kotlin.KtfmtStep.KtfmtFormattingOptions;
import com.diffplug.spotless.kotlin.KtfmtStep.Style;

public class KtfmtStepInnerStateTest {
    private static final String SOURCE = "fun main(){println(\"hi\")}";

    private final Provisioner provisioner = new FixtureProvisioner();

    @Test
    void createsModernFormatterWithDefaultStyle() throws Exception {
        assertThat(format("0.46", null, null)).isEqualTo("modern:" + SOURCE);
    }

    @Test
    void createsModernFormatterWithExplicitStyle() throws Exception {
        assertThat(format("0.46", Style.GOOGLE, null)).isEqualTo("modern:" + SOURCE);
    }

    @Test
    void createsModernFormatterWithFormattingOptions() throws Exception {
        final KtfmtFormattingOptions options = options();

        assertThat(format("0.46", null, options)).isEqualTo("modern:" + SOURCE);
    }

    @Test
    void createsModernFormatterWithStyleAndFormattingOptions() throws Exception {
        final KtfmtFormattingOptions options = options();

        assertThat(format("0.46", Style.KOTLINLANG, options)).isEqualTo("modern:" + SOURCE);
    }

    @Test
    void formatsWithFallbackDefaultMethodFromBothLegacyFormatterPackages() throws Exception {
        assertThat(format("0.31", Style.DEFAULT, null)).isEqualTo("format-default:" + SOURCE);
        assertThat(format("0.30", Style.DEFAULT, null)).isEqualTo("old-default:" + SOURCE);
    }

    @Test
    void formatsWithFallbackFormattingOptionFieldsFromBothLegacyFormatterPackages() throws Exception {
        assertThat(format("0.31", Style.DROPBOX, null)).isEqualTo("format-options:" + SOURCE);
        assertThat(format("0.30", Style.DROPBOX, null)).isEqualTo("old-options:" + SOURCE);
    }

    @Test
    void formatsWithFallbackCompanionOptionsBeforePublicFieldsWereAvailable() throws Exception {
        assertThat(format("0.18", Style.DROPBOX, null)).isEqualTo("pre-field-options:" + SOURCE);
    }

    private String format(String version, Style style, KtfmtFormattingOptions options) throws Exception {
        final FormatterStep step = KtfmtStep.create(version, provisioner, style, options);
        return step.format(SOURCE, Formatter.NO_FILE_SENTINEL);
    }

    private static KtfmtFormattingOptions options() {
        final KtfmtFormattingOptions options = new KtfmtFormattingOptions();
        options.setMaxWidth(120);
        options.setBlockIndent(2);
        options.setContinuationIndent(4);
        options.setRemoveUnusedImport(true);
        return options;
    }

    private static final class FixtureProvisioner implements Provisioner {
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            final Set<File> files = new LinkedHashSet<>();
            for (String mavenCoordinate : mavenCoordinates) {
                files.addAll(extractFixtureJars(versionFrom(mavenCoordinate)));
            }
            return files;
        }

        private static String versionFrom(String mavenCoordinate) {
            final int versionSeparator = mavenCoordinate.lastIndexOf(':');
            if (versionSeparator < 0 || versionSeparator == mavenCoordinate.length() - 1) {
                throw new IllegalArgumentException("Expected Maven coordinate with a version: " + mavenCoordinate);
            }
            return mavenCoordinate.substring(versionSeparator + 1);
        }

        private static Set<File> extractFixtureJars(String version) {
            try {
                final Set<File> files = new LinkedHashSet<>();
                final String indexResource = "ktfmt-fixtures/" + version + ".classpath";
                try (InputStream inputStream = resource(indexResource);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String jarResource;
                    while ((jarResource = reader.readLine()) != null) {
                        if (!jarResource.isBlank()) {
                            files.add(extractResource(jarResource).toFile());
                        }
                    }
                }
                return files;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to extract ktfmt fixture jars for version " + version, e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("ktfmt-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = KtfmtStepInnerStateTest.class.getClassLoader().getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
