/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.generic.PipeStepPair;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.FormatterFactory;
import com.diffplug.spotless.maven.FormatterStepConfig;
import com.diffplug.spotless.maven.FormatterStepFactory;
import com.diffplug.spotless.maven.antlr4.Antlr4;
import com.diffplug.spotless.maven.cpp.Cpp;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.Format;
import com.diffplug.spotless.maven.generic.ToggleOffOn;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.gherkin.Gherkin;
import com.diffplug.spotless.maven.go.Go;
import com.diffplug.spotless.maven.groovy.Groovy;
import com.diffplug.spotless.maven.java.Java;
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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SpotlessMavenPluginTest {
    private static final File SAMPLE_FILE = new File("Sample.txt");

    @TempDir
    Path projectDirectory;

    @Test
    void formatterStepConfigExposesConfiguredValues() {
        Provisioner provisioner = (withTransitives, mavenCoordinates) -> Set.of();
        FormatterStepConfig config = new FormatterStepConfig(
                StandardCharsets.UTF_16,
                "(package|import)",
                Optional.of("origin/main"),
                provisioner,
                null,
                Optional.of("true"));

        assertThat(config.getEncoding()).isEqualTo(StandardCharsets.UTF_16);
        assertThat(config.getLicenseHeaderDelimiter()).isEqualTo("(package|import)");
        assertThat(config.getRatchetFrom()).contains("origin/main");
        assertThat(config.getProvisioner()).isSameAs(provisioner);
        assertThat(config.getFileLocator()).isNull();
        assertThat(config.spotlessSetLicenseHeaderYearsFromGitHistory()).contains("true");
    }

    @Test
    void formatterConfigExposesImmutableGlobalFormatterSteps() {
        Provisioner provisioner = (withTransitives, mavenCoordinates) -> Set.of();
        TrimTrailingWhitespace globalStep = new TrimTrailingWhitespace();
        FormatterConfig config = new FormatterConfig(
                projectDirectory.toFile(),
                StandardCharsets.UTF_8.name(),
                LineEnding.UNIX,
                Optional.empty(),
                provisioner,
                null,
                List.of(globalStep),
                Optional.empty());

        assertThat(config.getEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(config.getLineEndings()).isEqualTo(LineEnding.UNIX);
        assertThat(config.getRatchetFrom()).isEmpty();
        assertThat(config.getProvisioner()).isSameAs(provisioner);
        assertThat(config.getFileLocator()).isNull();
        assertThat(config.getGlobalStepFactories()).containsExactly(globalStep);
        assertThat(config.getSpotlessSetLicenseHeaderYearsFromGitHistory()).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> config.getGlobalStepFactories().add(new EndWithNewline()));
    }

    @Test
    void genericWhitespaceStepsFormatContent() throws Exception {
        FormatterStepConfig config = defaultStepConfig();
        FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(config);
        FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(config);

        assertThat(trimTrailingWhitespace.getName()).containsIgnoringCase("trailing");
        assertThat(trimTrailingWhitespace.format("alpha  \n\tbeta\t\n", SAMPLE_FILE))
                .isEqualTo("alpha\n\tbeta\n");
        assertThat(endWithNewline.getName()).containsIgnoringCase("newline");
        assertThat(endWithNewline.format("alpha", SAMPLE_FILE)).isEqualTo("alpha\n");
        assertThat(endWithNewline.format("alpha\n", SAMPLE_FILE)).isEqualTo("alpha\n");
    }

    @Test
    void toggleOffOnPreservesDisabledRegionsAroundFormatterSteps() throws Exception {
        FormatterStepConfig config = defaultStepConfig();
        FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(config);
        ToggleOffOn toggle = new ToggleOffOn();
        toggle.off = "// formatter:off";
        toggle.on = "// formatter:on";
        PipeStepPair pair = toggle.createPair();

        String input = "formatted  \n// formatter:off\nleft alone  \n// formatter:on\nformatted again  \n";
        String masked = pair.in().format(input, SAMPLE_FILE);
        String trimmed = trimTrailingWhitespace.format(masked, SAMPLE_FILE);
        String restored = pair.out().format(trimmed, SAMPLE_FILE);

        assertThat(restored).isEqualTo(
                "formatted\n// formatter:off\nleft alone  \n// formatter:on\nformatted again\n");
    }

    @Test
    void languageFormattersExposeDefaultIncludesAndLicenseHeaderDelimiters() {
        MavenProject project = mavenProjectWithStandardJavaSourceDirectories();

        assertThat(new Java().defaultIncludes(project))
                .containsExactlyInAnyOrder("src/main/java/**/*.java", "src/test/java/**/*.java");
        assertThat(new Java().licenseHeaderDelimiter().trim()).isEqualTo("(package|import|public|class|module)");

        assertThat(new Kotlin().defaultIncludes(project))
                .containsExactlyInAnyOrder("src/main/kotlin/**/*.kt", "src/test/kotlin/**/*.kt");
        assertThat(new Kotlin().licenseHeaderDelimiter()).isEqualTo("(package |@file|import )");

        assertThat(new Scala().defaultIncludes(project))
                .containsExactlyInAnyOrder(
                        "src/main/scala/**/*.scala",
                        "src/test/scala/**/*.scala",
                        "src/main/scala/**/*.sc",
                        "src/test/scala/**/*.sc");
        assertThat(new Scala().licenseHeaderDelimiter().trim()).isEqualTo("(package|import|public|class|module)");

        assertThat(new Groovy().defaultIncludes(project))
                .containsExactlyInAnyOrder("src/main/groovy/**/*.groovy", "src/test/groovy/**/*.groovy");
        assertThat(new Groovy().licenseHeaderDelimiter().trim()).isEqualTo("(package|import|public|class|module)");

        assertThat(new Pom().defaultIncludes(project)).containsExactly("pom.xml");
        assertThat(new Pom().licenseHeaderDelimiter()).isNull();
    }

    @Test
    void optionalFormattersStartWithoutImplicitIncludes() {
        MavenProject project = mavenProjectWithStandardJavaSourceDirectories();
        List<FormatterFactory> formatters = List.of(
                new Format(),
                new Markdown(),
                new Json(),
                new Yaml(),
                new Python(),
                new Shell(),
                new Sql(),
                new Go(),
                new Javascript(),
                new Typescript(),
                new Cpp(),
                new Gherkin());

        for (FormatterFactory formatter : formatters) {
            assertThat(formatter.defaultIncludes(project)).as(formatter.getClass().getSimpleName()).isEmpty();
            assertThat(formatter.includes()).as(formatter.getClass().getSimpleName()).isEmpty();
            assertThat(formatter.excludes()).as(formatter.getClass().getSimpleName()).isEmpty();
        }
    }

    @Test
    void generatedGrammarFormatterUsesAntlrDefaults() {
        Antlr4 antlr4 = new Antlr4();

        Set<String> antlrIncludes = antlr4.defaultIncludes(mavenProjectWithStandardJavaSourceDirectories());

        assertThat(antlrIncludes).hasSize(1);
        assertThat(antlrIncludes.iterator().next()).contains("*.g4");
        assertThat(antlr4.licenseHeaderDelimiter()).isNotBlank();
    }

    @Test
    void formatterFactoriesAcceptGlobalWhitespaceSteps() throws Exception {
        FormatterStepFactory trimTrailingWhitespace = new TrimTrailingWhitespace();
        FormatterStepFactory endWithNewline = new EndWithNewline();
        FormatterStepConfig config = defaultStepConfig();

        assertThat(trimTrailingWhitespace.newFormatterStep(config).format("value  \n", SAMPLE_FILE))
                .isEqualTo("value\n");
        assertThat(endWithNewline.newFormatterStep(config).format("value", SAMPLE_FILE))
                .isEqualTo("value\n");
    }

    private FormatterStepConfig defaultStepConfig() {
        return new FormatterStepConfig(
                StandardCharsets.UTF_8,
                null,
                Optional.empty(),
                (withTransitives, mavenCoordinates) -> Set.of(),
                null,
                Optional.empty());
    }

    private MavenProject mavenProjectWithStandardJavaSourceDirectories() {
        Path pom = projectDirectory.resolve("pom.xml");
        Build build = new Build();
        build.setSourceDirectory(projectDirectory.resolve("src/main/java").toString());
        build.setTestSourceDirectory(projectDirectory.resolve("src/test/java").toString());

        MavenProject project = new MavenProject();
        project.setFile(pom.toFile());
        project.setBuild(build);
        return project;
    }
}
