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
import java.util.Locale;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.javadoc.JavadocReport;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJavadocMojoTest {
    @TempDir
    Path directory;

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
        new BasicComponentConfigurator().configureComponent(report, configuration, new ExpressionEvaluator() {
            @Override
            public Object evaluate(String expression) {
                return "${project}".equals(expression) ? project : expression;
            }

            @Override
            public File alignToBaseDirectory(File file) {
                return file.isAbsolute() ? file : directory.resolve(file.getPath()).toFile();
            }
        }, null);
        report.setDestDir("apidocs");
        Path output = directory.resolve("apidocs");
        report.setReportOutputDirectory(output.toFile());

        report.generate(null, Locale.ENGLISH);

        assertThat(Files.readString(output.resolve("example/Greeting.html")))
                .contains("A greeting service.", "Returns a greeting.");
        assertThat(Files.readString(output.resolve("stylesheet.css"))).contains("body");
        assertThat(Files.readString(directory.resolve("options/package-list"))).contains("java.lang");
    }
}
