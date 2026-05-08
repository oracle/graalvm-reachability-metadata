/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.generic.PipeStepPair;
import com.diffplug.spotless.maven.ArtifactResolutionException;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.FormatterStepConfig;
import com.diffplug.spotless.maven.FormatterStepFactory;
import com.diffplug.spotless.maven.antlr4.Antlr4;
import com.diffplug.spotless.maven.cpp.Cpp;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.Format;
import com.diffplug.spotless.maven.generic.Indent;
import com.diffplug.spotless.maven.generic.Replace;
import com.diffplug.spotless.maven.generic.ReplaceRegex;
import com.diffplug.spotless.maven.generic.ToggleOffOn;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.go.Go;
import com.diffplug.spotless.maven.javascript.Javascript;
import com.diffplug.spotless.maven.json.Json;
import com.diffplug.spotless.maven.kotlin.Kotlin;
import com.diffplug.spotless.maven.markdown.Markdown;
import com.diffplug.spotless.maven.pom.Pom;
import com.diffplug.spotless.maven.python.Python;
import com.diffplug.spotless.maven.scala.Scala;
import com.diffplug.spotless.maven.shell.Shell;
import com.diffplug.spotless.maven.sql.Sql;
import com.diffplug.spotless.maven.typescript.Typescript;
import com.diffplug.spotless.maven.yaml.Yaml;

public class Spotless_maven_pluginTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void formatterConfigurationsExposeSuppliedValues() {
        Provisioner provisioner = (withTransitives, mavenCoordinates) -> Set.of(temporaryDirectory.toFile());
        List<FormatterStepFactory> globalSteps = new ArrayList<>();
        TrimTrailingWhitespace trimTrailingWhitespace = new TrimTrailingWhitespace();
        globalSteps.add(trimTrailingWhitespace);

        FormatterConfig formatterConfig = new FormatterConfig(
                temporaryDirectory.toFile(),
                StandardCharsets.UTF_8.name(),
                LineEnding.UNIX,
                Optional.of("origin/main"),
                provisioner,
                null,
                globalSteps,
                Optional.of("git-history"));

        assertThat(formatterConfig.getEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(formatterConfig.getLineEndings()).isSameAs(LineEnding.UNIX);
        assertThat(formatterConfig.getRatchetFrom()).contains("origin/main");
        assertThat(formatterConfig.getProvisioner().provisionWithTransitives(false, List.of("group:artifact:version")))
                .containsExactly(temporaryDirectory.toFile());
        assertThat(formatterConfig.getGlobalStepFactories()).containsExactly(trimTrailingWhitespace);
        assertThat(formatterConfig.getSpotlessSetLicenseHeaderYearsFromGitHistory()).contains("git-history");
        assertThat(formatterConfig.getFileLocator()).isNull();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> formatterConfig.getGlobalStepFactories().add(new EndWithNewline()));

        FormatterStepConfig stepConfig = new FormatterStepConfig(
                StandardCharsets.UTF_16,
                "package ",
                Optional.empty(),
                provisioner,
                null,
                Optional.empty());

        assertThat(stepConfig.getEncoding()).isEqualTo(StandardCharsets.UTF_16);
        assertThat(stepConfig.getLicenseHeaderDelimiter()).isEqualTo("package ");
        assertThat(stepConfig.getRatchetFrom()).isEmpty();
        assertThat(stepConfig.getProvisioner()).isSameAs(provisioner);
        assertThat(stepConfig.getFileLocator()).isNull();
        assertThat(stepConfig.spotlessSetLicenseHeaderYearsFromGitHistory()).isEmpty();
    }

    @Test
    void genericFormatterStepsTransformTextWithoutExternalTools() throws Exception {
        FormatterStepConfig stepConfig = new FormatterStepConfig(
                StandardCharsets.UTF_8,
                null,
                Optional.empty(),
                (withTransitives, mavenCoordinates) -> Set.of(),
                null,
                Optional.empty());
        File sentinel = Formatter.NO_FILE_SENTINEL;

        FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(stepConfig);
        FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(stepConfig);

        String trimmed = trimTrailingWhitespace.format("first line  \nsecond line\t", sentinel);
        assertThat(trimmed).isEqualTo("first line\nsecond line");
        assertThat(endWithNewline.format(trimmed, sentinel)).isEqualTo("first line\nsecond line\n");
    }

    @Test
    void toggleOffOnProtectsDisabledRegionsFromGenericSteps() throws Exception {
        FormatterStepConfig stepConfig = new FormatterStepConfig(
                StandardCharsets.UTF_8,
                null,
                Optional.empty(),
                (withTransitives, mavenCoordinates) -> Set.of(),
                null,
                Optional.empty());
        File sentinel = Formatter.NO_FILE_SENTINEL;
        ToggleOffOn toggle = new ToggleOffOn();
        PipeStepPair pair = toggle.createPair();
        FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(stepConfig);
        FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(stepConfig);
        String input = "formatted   \n// spotless:off\nleft alone   \n// spotless:on\nformatted too   ";

        String protectedInput = pair.in().format(input, sentinel);
        String formatted = endWithNewline.format(trimTrailingWhitespace.format(protectedInput, sentinel), sentinel);
        String restored = pair.out().format(formatted, sentinel);

        assertThat(restored).isEqualTo("formatted\n"
                + "// spotless:off\n"
                + "left alone   \n"
                + "// spotless:on\n"
                + "formatted too\n");
    }

    @Test
    void invalidGenericStepConfigurationsFailFast() {
        FormatterStepConfig stepConfig = new FormatterStepConfig(
                StandardCharsets.UTF_8,
                null,
                Optional.empty(),
                (withTransitives, mavenCoordinates) -> Set.of(),
                null,
                Optional.empty());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Replace().newFormatterStep(stepConfig))
                .withMessage("Must specify 'name' and 'search'.");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ReplaceRegex().newFormatterStep(stepConfig))
                .withMessage("Must specify 'name' and 'searchRegex'.");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Indent().newFormatterStep(stepConfig))
                .withMessage("Must specify exactly one of 'spaces' or 'tabs'.");
    }

    @Test
    void formatterFactoriesExposeDefaultIncludesAndLicenseHeaderDelimiters() {
        assertThat(new Format().defaultIncludes(null)).isEmpty();
        assertThat(new Format().licenseHeaderDelimiter()).isNull();
        assertThat(new Json().defaultIncludes(null)).isEmpty();
        assertThat(new Json().licenseHeaderDelimiter()).isNull();
        assertThat(new Go().defaultIncludes(null)).isEmpty();
        assertThat(new Python().defaultIncludes(null)).isEmpty();
        assertThat(new Markdown().defaultIncludes(null)).isEmpty();
        assertThat(new Shell().defaultIncludes(null)).isEmpty();
        assertThat(new Javascript().defaultIncludes(null)).isEmpty();
        assertThat(new Typescript().defaultIncludes(null)).isEmpty();
        assertThat(new Yaml().defaultIncludes(null)).isEmpty();
        assertThat(new Sql().defaultIncludes(null)).isEmpty();
        assertThat(new Pom().defaultIncludes(null)).containsExactly("pom.xml");
        assertThat(new Cpp().licenseHeaderDelimiter()).isNotBlank();
        assertThat(new Antlr4().defaultIncludes(null)).isNotEmpty();
        assertThat(new Antlr4().licenseHeaderDelimiter()).isNotBlank();
        assertThat(new Scala().defaultIncludes(null)).isNotEmpty();
        assertThat(new Scala().licenseHeaderDelimiter()).isNotBlank();
        assertThat(new Kotlin().defaultIncludes(null)).isNotEmpty();
        assertThat(new Kotlin().licenseHeaderDelimiter()).isNotBlank();
    }

    @Test
    void artifactResolutionExceptionPreservesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("resolver failed");
        ArtifactResolutionException exception = new ArtifactResolutionException("Unable to resolve artifacts", cause);

        assertThat(exception).hasMessage("Unable to resolve artifacts").hasCause(cause);
    }
}
