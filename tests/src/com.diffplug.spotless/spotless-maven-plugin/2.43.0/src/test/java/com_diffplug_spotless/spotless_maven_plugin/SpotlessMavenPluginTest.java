/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.FormatterStepConfig;
import com.diffplug.spotless.maven.FormatterStepFactory;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.Format;
import com.diffplug.spotless.maven.generic.Indent;
import com.diffplug.spotless.maven.generic.LicenseHeader;
import com.diffplug.spotless.maven.generic.Replace;
import com.diffplug.spotless.maven.generic.ReplaceRegex;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.java.Java;
import com.diffplug.spotless.maven.json.Json;
import com.diffplug.spotless.maven.pom.Pom;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SpotlessMavenPluginTest {
    private static final Provisioner EMPTY_PROVISIONER = (withTransitives, mavenCoordinates) -> Set.of();

    @TempDir
    private Path tempDir;

    @Test
    void genericFormatterStepsTrimWhitespaceAndAddFinalNewline() throws IOException {
        Path source = tempDir.resolve("sample.txt");
        Files.writeString(source, "alpha   \nbeta\t", StandardCharsets.UTF_8);

        FormatterStep trimWhitespace = new TrimTrailingWhitespace().newFormatterStep(formatterStepConfig());
        FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(formatterStepConfig());

        try (Formatter formatter = Formatter.builder()
                .name("spotless-maven-generic")
                .encoding(StandardCharsets.UTF_8)
                .lineEndingsPolicy(LineEnding.UNIX.createPolicy())
                .steps(List.of(trimWhitespace, endWithNewline))
                .rootDir(tempDir)
                .build()) {
            assertThat(formatter.compute("alpha   \nbeta\t", source.toFile())).isEqualTo("alpha\nbeta\n");
            assertThat(formatter.isClean(source.toFile())).isFalse();

            formatter.applyTo(source.toFile());

            assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo("alpha\nbeta\n");
            assertThat(formatter.isClean(source.toFile())).isTrue();
        }
    }

    @Test
    void formatterFactoriesExposeMavenDefaults() throws IOException {
        MavenProject project = mavenProject(tempDir);
        String mainJavaMask = Path.of("src", "main", "java").toString() + File.separator + "**"
                + File.separator + "*.java";
        String testJavaMask = Path.of("src", "test", "java").toString() + File.separator + "**"
                + File.separator + "*.java";

        Java javaFormat = new Java();

        assertThat(javaFormat.defaultIncludes(project)).containsExactlyInAnyOrder(mainJavaMask, testJavaMask);
        assertThat(javaFormat.licenseHeaderDelimiter()).isEqualTo("(package|import|public|class|module) ");
        assertThat(new Pom().defaultIncludes(project)).containsExactly("pom.xml");
        assertThat(new Pom().licenseHeaderDelimiter()).isNull();
        assertThat(new Format().defaultIncludes(project)).isEmpty();
        assertThat(new Json().defaultIncludes(project)).isEmpty();
    }

    @Test
    void formatterConfigurationObjectsPreserveGlobalSettings() {
        TrimTrailingWhitespace globalStepFactory = new TrimTrailingWhitespace();
        List<FormatterStepFactory> globalStepFactories = List.of(globalStepFactory);
        FormatterConfig formatterConfig = new FormatterConfig(
                tempDir.toFile(),
                StandardCharsets.UTF_16.name(),
                LineEnding.WINDOWS,
                Optional.of("origin/main"),
                EMPTY_PROVISIONER,
                null,
                globalStepFactories,
                Optional.of("true"));

        assertThat(formatterConfig.getEncoding()).isEqualTo(StandardCharsets.UTF_16.name());
        assertThat(formatterConfig.getLineEndings()).isEqualTo(LineEnding.WINDOWS);
        assertThat(formatterConfig.getRatchetFrom()).contains("origin/main");
        assertThat(formatterConfig.getProvisioner()).isSameAs(EMPTY_PROVISIONER);
        assertThat(formatterConfig.getFileLocator()).isNull();
        assertThat(formatterConfig.getSpotlessSetLicenseHeaderYearsFromGitHistory()).contains("true");
        assertThat(formatterConfig.getGlobalStepFactories()).containsExactly(globalStepFactory);
        assertThatThrownBy(() -> formatterConfig.getGlobalStepFactories().add(new EndWithNewline()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void formatterStepConfigurationSuppliesStepContext() {
        FormatterStepConfig stepConfig = new FormatterStepConfig(
                StandardCharsets.UTF_8,
                "(package|import)",
                Optional.of("main"),
                EMPTY_PROVISIONER,
                null,
                Optional.of("false"));

        assertThat(stepConfig.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(stepConfig.getLicenseHeaderDelimiter()).isEqualTo("(package|import)");
        assertThat(stepConfig.getRatchetFrom()).contains("main");
        assertThat(stepConfig.getProvisioner()).isSameAs(EMPTY_PROVISIONER);
        assertThat(stepConfig.getFileLocator()).isNull();
        assertThat(stepConfig.spotlessSetLicenseHeaderYearsFromGitHistory()).contains("false");
    }

    @Test
    void invalidFormatterStepConfigurationFailsFastWithClearMessages() {
        FormatterStepConfig stepConfig = formatterStepConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Indent().newFormatterStep(stepConfig))
                .withMessageContaining("Must specify exactly one of 'spaces' or 'tabs'");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Replace().newFormatterStep(stepConfig))
                .withMessageContaining("Must specify 'name' and 'search'");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReplaceRegex().newFormatterStep(stepConfig))
                .withMessageContaining("Must specify 'name' and 'searchRegex'");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LicenseHeader().newFormatterStep(stepConfig))
                .withMessageContaining("Must specify exactly one of 'file' or 'content'");
    }

    private static FormatterStepConfig formatterStepConfig() {
        return new FormatterStepConfig(
                StandardCharsets.UTF_8,
                "(package|import|public|class|module)",
                Optional.empty(),
                EMPTY_PROVISIONER,
                null,
                Optional.empty());
    }

    private static MavenProject mavenProject(Path baseDir) throws IOException {
        Files.createDirectories(baseDir.resolve(Path.of("src", "main", "java")));
        Files.createDirectories(baseDir.resolve(Path.of("src", "test", "java")));
        Path pom = baseDir.resolve("pom.xml");
        Files.writeString(pom, "<project/>\n", StandardCharsets.UTF_8);

        Build build = new Build();
        build.setSourceDirectory(baseDir.resolve(Path.of("src", "main", "java")).toString());
        build.setTestSourceDirectory(baseDir.resolve(Path.of("src", "test", "java")).toString());
        Model model = new Model();
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(pom.toFile());
        return project;
    }
}
