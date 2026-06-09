/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.generic.PipeStepPair;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.FormatterStepConfig;
import com.diffplug.spotless.maven.FormatterStepFactory;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.ToggleOffOn;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.groovy.RemoveSemicolons;
import com.diffplug.spotless.maven.java.ImportOrder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class Spotless_maven_pluginTest {
    private static final File NO_FILE = Formatter.NO_FILE_SENTINEL;
    private static final Provisioner NO_PROVISIONER = (withTransitives, mavenCoordinates) -> Collections.emptySet();

    @Test
    void genericStepFactoriesTrimTrailingWhitespaceAndEnsureFinalNewline() throws Exception {
        FormatterStepConfig config = stepConfig();
        FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(config);
        FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(config);

        String source = "alpha  \n\n beta\t";
        String trimmed = trimTrailingWhitespace.format(source, NO_FILE);
        String formatted = endWithNewline.format(trimmed, NO_FILE);

        assertThat(trimTrailingWhitespace.getName()).isEqualTo("trimTrailingWhitespace");
        assertThat(endWithNewline.getName()).isEqualTo("endWithNewline");
        assertThat(formatted).isEqualTo("alpha\n\n beta\n");
    }

    @Test
    void importOrderFactoryLeavesJavaSourceWithoutImportsUnchanged() throws Exception {
        FormatterStep importOrder = new ImportOrder().newFormatterStep(stepConfig());
        String source = """
                package example;

                class Sample {
                    String value() {
                        return "spotless";
                    }
                }
                """;

        assertThat(importOrder.getName()).isEqualTo("importOrder");
        assertThat(importOrder.format(source, NO_FILE)).isEqualTo(source);
    }

    @Test
    void toggleOffOnCreatesRoundTrippingFormatterPipeSteps() throws Exception {
        ToggleOffOn toggle = new ToggleOffOn();
        toggle.off = "// spotless:off";
        toggle.on = "// spotless:on";
        PipeStepPair pair = toggle.createPair();
        String source = """
                class Sample {
                    // spotless:off
                    String  intentionallyOdd   = "kept";
                    // spotless:on
                }
                """;

        String protectedText = pair.in().format(source, NO_FILE);
        String restoredText = pair.out().format(protectedText, NO_FILE);

        assertThat(pair.in().getName()).isNotBlank();
        assertThat(pair.out().getName()).isNotBlank();
        assertThat(restoredText).isEqualTo(source);
    }

    @Test
    void removeSemicolonsFactoryRemovesUnnecessaryGroovyStatementSemicolons() throws Exception {
        FormatterStep removeSemicolons = new RemoveSemicolons().newFormatterStep(stepConfig());
        String source = "def answer = 42;\nprintln answer;\n";
        String expected = "def answer = 42" + System.lineSeparator()
                + "println answer" + System.lineSeparator();

        assertThat(removeSemicolons.getName()).isEqualTo("Remove unnecessary semicolons");
        assertThat(removeSemicolons.format(source, NO_FILE)).isEqualTo(expected);
    }

    @Test
    void formatterStepConfigExposesConfiguredValues() {
        FormatterStepConfig config = stepConfig();

        assertThat(config.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(config.getLicenseHeaderDelimiter()).isEqualTo("package ");
        assertThat(config.getRatchetFrom()).contains("origin/main");
        assertThat(config.getProvisioner()).isSameAs(NO_PROVISIONER);
        assertThat(config.getFileLocator()).isNull();
        assertThat(config.spotlessSetLicenseHeaderYearsFromGitHistory()).isEmpty();
    }

    @Test
    void formatterConfigExposesGlobalStepFactoriesAsUnmodifiableList() {
        List<FormatterStepFactory> globalStepFactories = new ArrayList<>();
        TrimTrailingWhitespace trimTrailingWhitespace = new TrimTrailingWhitespace();
        globalStepFactories.add(trimTrailingWhitespace);
        FormatterConfig config = new FormatterConfig(
                null,
                StandardCharsets.UTF_8.name(),
                LineEnding.UNIX,
                Optional.empty(),
                NO_PROVISIONER,
                null,
                globalStepFactories,
                Optional.empty());

        assertThat(config.getEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(config.getLineEndings()).isEqualTo(LineEnding.UNIX);
        assertThat(config.getRatchetFrom()).isEmpty();
        assertThat(config.getProvisioner()).isSameAs(NO_PROVISIONER);
        assertThat(config.getFileLocator()).isNull();
        assertThat(config.getSpotlessSetLicenseHeaderYearsFromGitHistory()).isEmpty();
        assertThat(config.getGlobalStepFactories()).containsExactly(trimTrailingWhitespace);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> config.getGlobalStepFactories().add(new EndWithNewline()));
    }

    private static FormatterStepConfig stepConfig() {
        return new FormatterStepConfig(
                StandardCharsets.UTF_8,
                "package ",
                Optional.of("origin/main"),
                NO_PROVISIONER,
                null,
                Optional.empty());
    }
}
