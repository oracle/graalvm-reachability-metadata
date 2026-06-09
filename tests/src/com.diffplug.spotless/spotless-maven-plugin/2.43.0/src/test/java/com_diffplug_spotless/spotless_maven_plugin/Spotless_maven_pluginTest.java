/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.FormatterStepConfig;
import com.diffplug.spotless.maven.FormatterStepFactory;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.Format;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.java.FormatAnnotations;
import com.diffplug.spotless.maven.java.Java;

public class Spotless_maven_pluginTest {
    private static final Provisioner NOOP_PROVISIONER = new NoopProvisioner();

    @TempDir
    private Path tempDir;

    @Test
    void genericFormatterStepsCleanRealProjectFile() throws Exception {
        final Path sourceFile = tempDir.resolve("src/main/resources/example.txt");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "alpha  \n beta\t", StandardCharsets.UTF_8);

        final FormatterStepConfig config = stepConfig();
        final FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(config);
        final FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(config);

        final String trimmed = trimTrailingWhitespace.format(Files.readString(sourceFile), sourceFile.toFile());
        final String formatted = endWithNewline.format(trimmed, sourceFile.toFile());

        assertThat(trimTrailingWhitespace.getName()).containsIgnoringCase("trailing");
        assertThat(endWithNewline.getName()).containsIgnoringCase("newline");
        assertThat(formatted).isEqualTo("alpha\n beta\n");
    }

    @Test
    void endWithNewlineIsIdempotentForConfiguredTextFile() throws Exception {
        final Path sourceFile = tempDir.resolve("src/main/resources/complete.txt");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "already complete\n", StandardCharsets.UTF_8);

        final FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(stepConfig());

        assertThat(endWithNewline.format(Files.readString(sourceFile), sourceFile.toFile()))
                .isEqualTo("already complete\n");
    }

    @Test
    void formatterConfigurationObjectsExposeMavenPluginSettings() {
        final List<FormatterStepFactory> globalSteps = List.of(new TrimTrailingWhitespace());
        final FormatterConfig formatterConfig = new FormatterConfig(
                tempDir.toFile(),
                StandardCharsets.UTF_8.name(),
                LineEnding.UNIX,
                Optional.of("origin/main"),
                NOOP_PROVISIONER,
                null,
                globalSteps,
                Optional.of("true"));
        final FormatterStepConfig stepConfig = new FormatterStepConfig(
                StandardCharsets.UTF_8,
                "package|import",
                Optional.of("origin/main"),
                NOOP_PROVISIONER,
                null,
                Optional.of("true"));

        assertThat(formatterConfig.getEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(formatterConfig.getLineEndings()).isEqualTo(LineEnding.UNIX);
        assertThat(formatterConfig.getRatchetFrom()).contains("origin/main");
        assertThat(formatterConfig.getProvisioner()).isSameAs(NOOP_PROVISIONER);
        assertThat(formatterConfig.getFileLocator()).isNull();
        assertThat(formatterConfig.getSpotlessSetLicenseHeaderYearsFromGitHistory()).contains("true");
        assertThrows(UnsupportedOperationException.class,
                () -> formatterConfig.getGlobalStepFactories().add(new EndWithNewline()));

        assertThat(stepConfig.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(stepConfig.getLicenseHeaderDelimiter()).isEqualTo("package|import");
        assertThat(stepConfig.getRatchetFrom()).contains("origin/main");
        assertThat(stepConfig.getProvisioner()).isSameAs(NOOP_PROVISIONER);
        assertThat(stepConfig.getFileLocator()).isNull();
        assertThat(stepConfig.spotlessSetLicenseHeaderYearsFromGitHistory()).contains("true");
    }

    @Test
    void javaFormatAnnotationsStepKeepsTypeAnnotationWithType() throws Exception {
        final Path sourceFile = tempDir.resolve("src/main/java/example/Annotated.java");
        Files.createDirectories(sourceFile.getParent());
        final String javaSource = """
                package example;

                class Annotated {
                    private @Nullable
                            String name;
                }
                """;
        Files.writeString(sourceFile, javaSource, StandardCharsets.UTF_8);

        final FormatterStep formatAnnotations = new FormatAnnotations().newFormatterStep(stepConfig());

        assertThat(formatAnnotations.getName()).containsIgnoringCase("annotation");
        assertThat(formatAnnotations.format(Files.readString(sourceFile), sourceFile.toFile()))
                .isEqualTo("""
                        package example;

                        class Annotated {
                            private @Nullable String name;
                        }
                        """);
    }

    @Test
    void javaFactoryDerivesMavenSourceIncludesFromTemporaryProject() throws Exception {
        final Path projectDir = tempDir.resolve("project");
        final Path mainSources = projectDir.resolve("src/main/java");
        final Path testSources = projectDir.resolve("src/test/java");
        Files.createDirectories(mainSources);
        Files.createDirectories(testSources);
        Files.writeString(projectDir.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);

        final MavenProject project = new MavenProject();
        final Build build = new Build();
        build.setSourceDirectory(mainSources.toString());
        build.setTestSourceDirectory(testSources.toString());
        project.setBuild(build);
        project.setFile(projectDir.resolve("pom.xml").toFile());

        final Java java = new Java();

        assertThat(java.defaultIncludes(project)).containsExactlyInAnyOrder(
                javaFileMask("src", "main", "java"),
                javaFileMask("src", "test", "java"));
        assertThat(java.licenseHeaderDelimiter()).isEqualTo("(package|import|public|class|module) ");
        assertThat(java.includes()).isEmpty();
        assertThat(java.excludes()).isEmpty();
    }

    @Test
    void genericFormatFactoryHasEmptyDefaultsAndAcceptsSafeBuiltInSteps() {
        final Format format = new Format();

        format.addTrimTrailingWhitespace(new TrimTrailingWhitespace());
        format.addEndWithNewline(new EndWithNewline());

        assertThat(format.defaultIncludes(new MavenProject())).isEmpty();
        assertThat(format.licenseHeaderDelimiter()).isNull();
        assertThat(format.includes()).isEmpty();
        assertThat(format.excludes()).isEmpty();
    }

    private static FormatterStepConfig stepConfig() {
        return new FormatterStepConfig(
                StandardCharsets.UTF_8,
                null,
                Optional.empty(),
                NOOP_PROVISIONER,
                null,
                Optional.empty());
    }

    private static String javaFileMask(String... directories) {
        return String.join(File.separator, directories) + File.separator + "**/*.java";
    }

    private static final class NoopProvisioner implements Provisioner {
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return Collections.emptySet();
        }
    }
}
