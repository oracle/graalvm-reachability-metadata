/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.javadoc.FixJavadocMojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractFixJavadocMojoTest {
    @TempDir
    Path tempDir;

    @Test
    void fixGoalRecognizesInheritedMethodsAndUsesClirrGoalLookup() throws Exception {
        assertEquals(7, new FixJavadocChildFixture().inherited(7));

        Path projectDirectory = tempDir.resolve("project");
        Path sourceDirectory = projectDirectory.resolve("src/main/java");
        Path localRepositoryDirectory = projectDirectory.resolve("local-repository");
        Files.createDirectories(sourceDirectory.resolve("org_apache_maven_plugins/maven_javadoc_plugin"));
        Files.createDirectories(localRepositoryDirectory);
        Files.writeString(sourceDirectory.resolve(
                "org_apache_maven_plugins/maven_javadoc_plugin/FixJavadocChildFixture.java"), """
                package org_apache_maven_plugins.maven_javadoc_plugin;

                class FixJavadocChildFixture extends FixJavadocParentFixture {
                    public int inherited(int value) {
                        return value;
                    }
                }
                """, StandardCharsets.UTF_8);

        FixJavadocMojo mojo = new FixJavadocMojo();
        MojoFieldInjector injector = new MojoFieldInjector();
        MavenProject project = newProject(projectDirectory, sourceDirectory);

        injector.inject(mojo, "project", project);
        injector.inject(mojo, "settings", new Settings());
        injector.inject(mojo, "localRepository", localRepository(localRepositoryDirectory));
        injector.inject(mojo, "projectClassLoader", AbstractFixJavadocMojoTest.class.getClassLoader());
        injector.inject(mojo, "comparisonVersion", "(,1.0)");
        injector.inject(mojo, "defaultAuthor", "test");
        injector.inject(mojo, "defaultSince", "1.0");
        injector.inject(mojo, "defaultVersion", "test-version");
        injector.inject(mojo, "encoding", "UTF-8");
        injector.inject(mojo, "fixTags", "all");
        injector.inject(mojo, "fixClassComment", false);
        injector.inject(mojo, "fixFieldComment", false);
        injector.inject(mojo, "fixMethodComment", true);
        injector.inject(mojo, "force", true);
        injector.inject(mojo, "ignoreClirr", false);
        injector.inject(mojo, "includes", "**/*.java");
        injector.inject(mojo, "level", "public");
        injector.inject(mojo, "outputDirectory", sourceDirectory.toFile());

        String previousMavenHome = System.getProperty("maven.home");
        System.setProperty("maven.home", "");
        try {
            mojo.execute();
        } finally {
            if (previousMavenHome == null) {
                System.clearProperty("maven.home");
            } else {
                System.setProperty("maven.home", previousMavenHome);
            }
        }

        Path fixedSource = sourceDirectory.resolve(
                "org_apache_maven_plugins/maven_javadoc_plugin/FixJavadocChildFixture.java");
        assertTrue(Files.isRegularFile(fixedSource));
        String fixedContent = Files.readString(fixedSource, StandardCharsets.UTF_8);
        assertTrue(fixedContent.contains("/** {@inheritDoc} */"));
    }

    private static MavenProject newProject(Path projectDirectory, Path sourceDirectory) throws IOException {
        Files.createDirectories(projectDirectory.resolve("target/classes"));
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("example");
        model.setArtifactId("fix-javadoc-project");
        model.setVersion("1.0");
        model.setPackaging("jar");

        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        build.setSourceDirectory(sourceDirectory.toString());
        build.setOutputDirectory(projectDirectory.resolve("target/classes").toString());
        model.setBuild(build);

        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Files.writeString(project.getFile().toPath(), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>fix-javadoc-project</artifactId>
                  <version>1.0</version>
                </project>
                """, StandardCharsets.UTF_8);
        project.addCompileSourceRoot(sourceDirectory.toString());
        project.setArtifact(newArtifact());
        project.setArtifacts(Collections.emptySet());
        project.setDependencyArtifacts(Collections.emptySet());
        return project;
    }

    private static Artifact newArtifact() {
        DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");
        return new DefaultArtifact("example", "fix-javadoc-project", VersionRange.createFromVersion("1.0"),
                "compile", "jar", null, handler);
    }

    private static ArtifactRepository localRepository(Path localRepositoryDirectory) {
        return new DefaultArtifactRepository("local", localRepositoryDirectory.toUri().toString(),
                new DefaultRepositoryLayout());
    }

    static final class MojoFieldInjector extends AbstractMojoTestCase {
        void inject(Object target, String name, Object value) throws IllegalAccessException {
            setVariableValueToObject(target, name, value);
        }
    }
}

class FixJavadocParentFixture {
    public int inherited(int value) {
        return value;
    }
}

class FixJavadocChildFixture extends FixJavadocParentFixture {
    @Override
    public int inherited(int value) {
        return value;
    }
}
