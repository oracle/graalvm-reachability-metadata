/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.resource.PlexusResource;
import org.codehaus.plexus.resource.ResourceManager;
import org.codehaus.plexus.resource.loader.FileResourceCreationException;
import org.codehaus.plexus.resource.loader.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.FormatExceptionPolicyStrict;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.maven.FileLocator;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.SpotlessApplyMojo;
import com.diffplug.spotless.maven.SpotlessCheckMojo;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.Format;
import com.diffplug.spotless.maven.generic.ToggleOffOn;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.groovy.RemoveSemicolons;
import com.diffplug.spotless.maven.java.Java;
import com.diffplug.spotless.maven.pom.Pom;

public class SpotlessMavenPluginTest {
    private static final Provisioner EMPTY_PROVISIONER = (withTransitives, mavenCoordinates) -> Collections.emptySet();

    @TempDir
    private Path temporaryDirectory;

    @Test
    void javaFormatterCombinesGlobalAndLanguageSteps() throws Exception {
        final Java javaFormatter = new Java();
        javaFormatter.addEndWithNewline(new EndWithNewline());

        final FormatterConfig config = new FormatterConfig(
                temporaryDirectory.toFile(),
                "UTF-8",
                LineEnding.UNIX,
                Optional.empty(),
                EMPTY_PROVISIONER,
                fileLocator(),
                List.of(new TrimTrailingWhitespace()),
                Optional.empty());
        final Path sourceFile = Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {    }");

        try (Formatter formatter = javaFormatter.newFormatter(() -> List.of(sourceFile.toFile()), config)) {
            assertThat(formatter.getEncoding()).hasToString("UTF-8");
            assertThat(formatter.getSteps()).extracting(FormatterStep::getName)
                    .containsExactly("trimTrailingWhitespace", "endWithNewline");

            final String formatted = formatter.compute("class Example {    \n}\t", sourceFile.toFile());

            assertThat(formatted).isEqualTo("class Example {\n}\n");
        }
    }

    @Test
    void standaloneFormatterStepFactoriesCreateUsableSteps() throws Exception {
        final FormatterStep trimTrailingWhitespace = new TrimTrailingWhitespace().newFormatterStep(null);
        final FormatterStep endWithNewline = new EndWithNewline().newFormatterStep(null);

        assertThat(trimTrailingWhitespace.getName()).isEqualTo("trimTrailingWhitespace");
        assertThat(trimTrailingWhitespace.format("alpha  \n beta\t", new File("sample.txt"))).isEqualTo("alpha\n beta");
        assertThat(endWithNewline.getName()).isEqualTo("endWithNewline");
        assertThat(endWithNewline.format("alpha", new File("sample.txt"))).isEqualTo("alpha\n");
    }

    @Test
    void formatterTogglePreservesDisabledSections() throws Exception {
        final ToggleOffOn toggle = new ToggleOffOn();
        toggle.off = "spotless:off";
        toggle.on = "spotless:on";

        final Java javaFormatter = new Java();
        javaFormatter.addTrimTrailingWhitespace(new TrimTrailingWhitespace());
        javaFormatter.addToggleOffOn(toggle);

        final FormatterConfig config = new FormatterConfig(
                temporaryDirectory.toFile(),
                "UTF-8",
                LineEnding.UNIX,
                Optional.empty(),
                EMPTY_PROVISIONER,
                fileLocator(),
                List.of(),
                Optional.empty());
        final Path sourceFile = Files.writeString(
                temporaryDirectory.resolve("ToggleExample.java"),
                "class ToggleExample {}\n");

        try (Formatter formatter = javaFormatter.newFormatter(() -> List.of(sourceFile.toFile()), config)) {
            final String formatted = formatter.compute(
                    "before  \nspotless:off\nkept  \nspotless:on\nafter  ",
                    sourceFile.toFile());

            assertThat(formatted).isEqualTo("before\nspotless:off\nkept  \nspotless:on\nafter");
        }
    }

    @Test
    void groovyFormatterRemovesTrailingSemicolons() throws Exception {
        final Path sourceFile = Files.writeString(temporaryDirectory.resolve("Example.groovy"), "println 'ready';");
        final FormatterStep removeSemicolons = new RemoveSemicolons().newFormatterStep(null);

        try (Formatter formatter = Formatter.builder()
                .name("Groovy")
                .encoding(StandardCharsets.UTF_8)
                .lineEndingsPolicy(LineEnding.UNIX.createPolicy(temporaryDirectory.toFile(), () -> List.of(sourceFile.toFile())))
                .exceptionPolicy(new FormatExceptionPolicyStrict())
                .steps(List.of(removeSemicolons))
                .rootDir(temporaryDirectory)
                .build()) {
            assertThat(formatter.getSteps()).extracting(FormatterStep::getName)
                    .containsExactly("Remove unnecessary semicolons");

            final String formatted = formatter.compute(
                    "def answer = 42;\nprintln answer;\nprintln 'semi;colon';",
                    sourceFile.toFile());

            assertThat(formatted).isEqualTo("def answer = 42\nprintln answer\nprintln 'semi;colon'\n");
        }
    }

    @Test
    void formatterFactoriesExposeMavenDefaults() throws Exception {
        final Path projectDirectory = Files.createDirectories(temporaryDirectory.resolve("project"));
        final Path mainSources = Files.createDirectories(projectDirectory.resolve("src/main/java"));
        final Path testSources = Files.createDirectories(projectDirectory.resolve("src/test/java"));
        final MavenProject project = mavenProject(projectDirectory, mainSources, testSources);

        final Java javaFormatter = new Java();
        final Set<String> defaultJavaIncludes = javaFormatter.defaultIncludes(project);

        assertThat(defaultJavaIncludes).contains(
                toFileMask(projectDirectory.relativize(mainSources)),
                toFileMask(projectDirectory.relativize(testSources)));
        assertThat(javaFormatter.licenseHeaderDelimiter().trim()).isEqualTo("(package|import|public|class|module)");
        assertThat(new Pom().defaultIncludes(project)).containsExactly("pom.xml");
        assertThat(new Format().defaultIncludes(project)).isEmpty();
        assertThat(new Format().licenseHeaderDelimiter()).isNull();
    }

    @Test
    void pluginDescriptorAdvertisesApplyAndCheckMojos() throws Exception {
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        try (InputStream descriptor = SpotlessCheckMojo.class.getResourceAsStream("/META-INF/maven/plugin.xml")) {
            assertThat(descriptor).isNotNull();
            final Document pluginDescriptor = documentBuilderFactory.newDocumentBuilder().parse(descriptor);

            assertThat(textContents(pluginDescriptor, "goal")).contains("apply", "check");
            assertThat(textContents(pluginDescriptor, "implementation")).contains(
                    SpotlessApplyMojo.class.getName(),
                    SpotlessCheckMojo.class.getName());
        }

        assertThatCode(() -> new SpotlessApplyMojo()).doesNotThrowAnyException();
        assertThatCode(() -> new SpotlessCheckMojo()).doesNotThrowAnyException();
        assertThat(new SpotlessApplyMojo()).isInstanceOf(AbstractMojo.class);
        assertThat(new SpotlessCheckMojo()).isInstanceOf(AbstractMojo.class);
    }

    private FileLocator fileLocator() throws Exception {
        final Path buildDirectory = Files.createDirectories(temporaryDirectory.resolve("target"));
        return new FileLocator(new UnsupportedResourceManager(), temporaryDirectory.toFile(), buildDirectory.toFile());
    }

    private static MavenProject mavenProject(Path basedir, Path mainSources, Path testSources) {
        final Build build = new Build();
        build.setSourceDirectory(mainSources.toString());
        build.setTestSourceDirectory(testSources.toString());

        final Model model = new Model();
        model.setGroupId("example");
        model.setArtifactId("sample");
        model.setVersion("1.0");
        model.setBuild(build);

        final MavenProject project = new MavenProject(model);
        project.setFile(basedir.resolve("pom.xml").toFile());
        return project;
    }

    private static final class UnsupportedResourceManager implements ResourceManager {
        @Override
        public InputStream getResourceAsInputStream(String name) throws ResourceNotFoundException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public File getResourceAsFile(String name) throws ResourceNotFoundException, FileResourceCreationException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public File getResourceAsFile(String name, String outputFile)
                throws ResourceNotFoundException, FileResourceCreationException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public void setOutputDirectory(File outputDirectory) {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public void addSearchPath(String resourceLoaderId, String searchPath) {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public File resolveLocation(String location, String localfile) throws IOException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public File resolveLocation(String location) throws IOException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public PlexusResource getResource(String name) throws ResourceNotFoundException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public File getResourceAsFile(PlexusResource resource) throws FileResourceCreationException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }

        @Override
        public void createResourceAsFile(PlexusResource resource, File outputFile)
                throws FileResourceCreationException {
            throw new UnsupportedOperationException("Resource lookup is not used by this test");
        }
    }

    private static String toFileMask(Path relativeDirectory) {
        return relativeDirectory + File.separator + "**" + File.separator + "*.java";
    }

    private static List<String> textContents(Document document, String tagName) {
        final NodeList nodes = document.getElementsByTagName(tagName);
        return IntStream.range(0, nodes.getLength())
                .mapToObj(nodes::item)
                .map(node -> node.getTextContent().trim())
                .toList();
    }
}
