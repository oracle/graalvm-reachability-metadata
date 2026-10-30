/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.javadoc.JavadocReport;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AbstractJavadocMojoTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(120)
    void requiresALocalRepositoryDirectoryForReactorDocumentation() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("application/src/main/java/example"));
        Files.writeString(sources.resolve("Application.java"), """
                package example;
                /** The application entry point. */
                public class Application { }
                """);
        Model model = new Model();
        model.setGroupId("example");
        model.setArtifactId("application");
        model.setVersion("1");
        Build build = new Build();
        build.setDirectory(directory.resolve("application/target").toString());
        build.setOutputDirectory(directory.resolve("application/target/classes").toString());
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(directory.resolve("application/pom.xml").toFile());
        project.addCompileSourceRoot(sources.getParent().toString());

        Artifact dependency = new DefaultArtifact("example", "component", VersionRange.createFromVersion("1"),
                "compile", "jar", null, new DefaultArtifactHandler("jar"));
        project.setDependencyArtifacts(Set.of(dependency));
        Model moduleModel = new Model();
        moduleModel.setGroupId("example");
        moduleModel.setArtifactId("component");
        moduleModel.setVersion("1");
        moduleModel.setUrl("https://example.invalid/component");
        MavenProject module = new MavenProject(moduleModel);
        module.setArtifact(dependency);
        Path moduleDirectory = Files.createDirectories(directory.resolve("component"));
        Path modulePom = Files.writeString(moduleDirectory.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>component</artifactId>
                  <version>1</version>
                </project>
                """);
        module.setFile(modulePom.toFile());
        Path repositoryDirectory = directory.resolve("missing-local-repository");
        ArtifactRepository repository = new DefaultArtifactRepository("local",
                repositoryDirectory.toUri().toString(), new DefaultRepositoryLayout());

        JavadocReport report = new JavadocReport();
        GoalLog log = new GoalLog();
        report.setLog(log);
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        ((XmlPlexusConfiguration) configuration.getChild("project")).setValue("${project}");
        ((XmlPlexusConfiguration) configuration.getChild("reactorProjects")).setValue("${reactorProjects}");
        ((XmlPlexusConfiguration) configuration.getChild("localRepository")).setValue("${localRepository}");
        ((XmlPlexusConfiguration) configuration.getChild("detectOfflineLinks")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("stylesheet")).setValue("maven");
        ((XmlPlexusConfiguration) configuration.getChild("encoding")).setValue("UTF-8");
        ((XmlPlexusConfiguration) configuration.getChild("docencoding")).setValue("UTF-8");
        ((XmlPlexusConfiguration) configuration.getChild("failOnError")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("javadocOptionsDir"))
                .setValue(directory.resolve("application/options").toString());
        ExpressionEvaluator evaluator = new ExpressionEvaluator() {
            @Override
            public Object evaluate(String expression) {
                return switch (expression) {
                    case "${project}" -> project;
                    case "${reactorProjects}" -> List.of(module);
                    case "${localRepository}" -> repository;
                    default -> expression;
                };
            }

            @Override
            public File alignToBaseDirectory(File file) {
                return file.isAbsolute() ? file : directory.resolve(file.getPath()).toFile();
            }
        };
        new BasicComponentConfigurator().configureComponent(report, configuration, evaluator, null);
        report.setDestDir("apidocs");
        report.setReportOutputDirectory(directory.resolve("application/target/apidocs").toFile());

        assertThatIllegalArgumentException().isThrownBy(() -> report.generate(null, Locale.ENGLISH))
                .withMessage("localRepositoryDir '" + repositoryDirectory + "' should be a directory.");
        assertThat(log.output.toString()).containsPattern(
                "The goal 'org\\.apache\\.maven\\.plugins:maven-javadoc-plugin:[^:']+:javadoc'"
                        + " has not been previously called for the module: 'example:component:jar:1'");
    }

    private static final class GoalLog extends SystemStreamLog {
        private final StringBuilder output = new StringBuilder();

        @Override
        public void info(CharSequence message) {
            output.append(message).append('\n');
        }
    }

    @Test
    @Timeout(120)
    void generatesDocumentationWithTheMavenStylesheet() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src/main/java/example"));
        Files.writeString(sources.resolve("Greeting.java"), """
                package example;
                /** A greeting service. */
                public class Greeting {
                    /**
                     * Returns a greeting.
                     * @return the greeting
                     */
                    public String message() { return "hello"; }
                }
                """);
        Model model = new Model();
        model.setGroupId("example");
        model.setArtifactId("greeting");
        model.setName("Greeting service");
        Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        build.setOutputDirectory(directory.resolve("target/classes").toString());
        build.setSourceDirectory(sources.getParent().toString());
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(directory.resolve("pom.xml").toFile());
        project.addCompileSourceRoot(sources.getParent().toString());

        JavadocReport report = new JavadocReport();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        ((XmlPlexusConfiguration) configuration.getChild("project")).setValue("${project}");
        ((XmlPlexusConfiguration) configuration.getChild("failOnError")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("stylesheet")).setValue("maven");
        ((XmlPlexusConfiguration) configuration.getChild("encoding")).setValue("UTF-8");
        ((XmlPlexusConfiguration) configuration.getChild("docencoding")).setValue("UTF-8");
        ((XmlPlexusConfiguration) configuration.getChild("detectJavaApiLink")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("isOffline")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("javadocOptionsDir")).setValue(directory.resolve("options").toString());
        ExpressionEvaluator evaluator = new ExpressionEvaluator() {
            @Override
            public Object evaluate(String expression) {
                return "${project}".equals(expression) ? project : expression;
            }

            @Override
            public File alignToBaseDirectory(File file) {
                return file.isAbsolute() ? file : directory.resolve(file.getPath()).toFile();
            }
        };
        new BasicComponentConfigurator().configureComponent(report, configuration, evaluator, null);
        report.setDestDir("apidocs");
        Path output = directory.resolve("apidocs");
        report.setReportOutputDirectory(output.toFile());

        report.generate(null, Locale.ENGLISH);

        assertThat(Files.readString(output.resolve("example/Greeting.html")))
                .contains("A greeting service.", "Returns a greeting.");
        assertThat(Files.readString(output.resolve("stylesheet.css"))).contains("body");
        assertThat(Files.readString(directory.resolve("options/package-list"))).contains("java.lang");

        Files.writeString(sources.getParent().resolve("documentation.css"), """
                /* Project documentation theme. */
                body { color: #123456; }
                """);
        XmlPlexusConfiguration themeConfiguration = new XmlPlexusConfiguration("configuration");
        ((XmlPlexusConfiguration) themeConfiguration.getChild("stylesheetfile")).setValue("documentation.css");
        new BasicComponentConfigurator().configureComponent(report, themeConfiguration, evaluator, null);

        report.generate(null, Locale.ENGLISH);

        try (Stream<Path> generatedFiles = Files.walk(output)) {
            Path stylesheet = generatedFiles
                    .filter(path -> path.getFileName().toString().equals("documentation.css"))
                    .findFirst().orElseThrow();
            assertThat(Files.readString(stylesheet)).contains("Project documentation theme.", "#123456");
        }
        assertThat(Files.readString(output.resolve("example/Greeting.html")))
                .contains("A greeting service.", "documentation.css");
    }
}
