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
import java.util.Set;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.javadoc.FixJavadocMojo;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFixJavadocMojoTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(120)
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void addsClassDocumentationWhenOptionalApiComparisonIsUnavailable() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src/main/java/example"));
        String original = """
                package example;
                public class Greeting {
                }
                """;
        Path source = Files.writeString(sources.resolve("Greeting.java"), original);
        Model model = new Model();
        model.setGroupId("example");
        model.setArtifactId("greeting");
        model.setVersion("1");
        Build build = new Build();
        build.setSourceDirectory(sources.getParent().toString());
        build.setDirectory(Files.createDirectories(directory.resolve("target")).toString());
        build.setOutputDirectory(directory.resolve("target/classes").toString());
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setArtifacts(Set.of());
        project.addCompileSourceRoot(sources.getParent().toString());
        project.setFile(Files.writeString(directory.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>greeting</artifactId>
                  <version>1</version>
                </project>
                """).toFile());
        Path repositoryDirectory = Files.createDirectories(directory.resolve("repository"));
        ArtifactRepository repository = new DefaultArtifactRepository("local",
                repositoryDirectory.toUri().toString(), new DefaultRepositoryLayout());

        FixJavadocMojo mojo = new FixJavadocMojo();
        ComparisonLog log = new ComparisonLog();
        mojo.setLog(log);
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        ((XmlPlexusConfiguration) configuration.getChild("project")).setValue("${project}");
        ((XmlPlexusConfiguration) configuration.getChild("localRepository")).setValue("${localRepository}");
        ((XmlPlexusConfiguration) configuration.getChild("force")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("ignoreClirr")).setValue("false");
        ((XmlPlexusConfiguration) configuration.getChild("fixClassComment")).setValue("true");
        ((XmlPlexusConfiguration) configuration.getChild("fixTags")).setValue("author");
        ((XmlPlexusConfiguration) configuration.getChild("defaultAuthor")).setValue("Documentation Team");
        ((XmlPlexusConfiguration) configuration.getChild("defaultSince")).setValue("${projectVersion}");
        ((XmlPlexusConfiguration) configuration.getChild("comparisonVersion")).setValue("(,${projectVersion})");
        ((XmlPlexusConfiguration) configuration.getChild("encoding")).setValue("UTF-8");
        ((XmlPlexusConfiguration) configuration.getChild("level")).setValue("public");
        ((XmlPlexusConfiguration) configuration.getChild("includes")).setValue("**/*.java");
        ExpressionEvaluator evaluator = new ExpressionEvaluator() {
            @Override
            public Object evaluate(String expression) {
                return switch (expression) {
                    case "${project}" -> project;
                    case "${localRepository}" -> repository;
                    case "${projectVersion}" -> project.getVersion();
                    case "(,${projectVersion})" -> "(," + project.getVersion() + ")";
                    default -> expression;
                };
            }

            @Override
            public File alignToBaseDirectory(File file) {
                return file.isAbsolute() ? file : directory.resolve(file.getPath()).toFile();
            }
        };
        new BasicComponentConfigurator().configureComponent(mojo, configuration, evaluator, null);

        String previousMavenHome = System.getProperty("maven.home");
        try {
            System.setProperty("maven.home", "");
            mojo.execute();
        } finally {
            if (previousMavenHome == null) {
                System.clearProperty("maven.home");
            } else {
                System.setProperty("maven.home", previousMavenHome);
            }
        }

        assertThat(log.errors.toString()).contains("Could NOT invoke Maven because no Maven Home is defined.");
        assertThat(Files.readString(source)).contains("@author Documentation Team", "public class Greeting")
                .doesNotContain("@since");
    }

    private static final class ComparisonLog extends SystemStreamLog {
        private final StringBuilder errors = new StringBuilder();

        @Override
        public void error(CharSequence message) {
            errors.append(message).append('\n');
        }
    }
}
