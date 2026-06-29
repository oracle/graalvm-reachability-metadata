/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.javadoc.JavadocReport;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractJavadocMojoTest {
    @TempDir
    Path tempDir;

    @Test
    void reportGenerationUsesBundledAndProjectResources() throws Exception {
        Path projectDirectory = tempDir.resolve("project");
        Path sourceDirectory = projectDirectory.resolve("src/main/java");
        Path resourcesDirectory = projectDirectory.resolve("src/main/resources");
        Path javadocDirectory = projectDirectory.resolve("src/main/javadoc");
        Path classesDirectory = projectDirectory.resolve("target/classes");
        Path outputDirectory = projectDirectory.resolve("target/site/apidocs");
        Path javadocOptionsDirectory = projectDirectory.resolve("target/javadoc-bundle-options");
        Files.createDirectories(sourceDirectory.resolve("example"));
        Files.createDirectories(resourcesDirectory.resolve("docs"));
        Files.createDirectories(javadocDirectory);
        Files.createDirectories(classesDirectory);
        Files.writeString(sourceDirectory.resolve("example/Example.java"), """
                package example;

                /** Example API. */
                public class Example {
                    /** Returns a greeting. */
                    public String greeting() {
                        return "hello";
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(resourcesDirectory.resolve("docs/help.html"), """
                <!doctype html>
                <html><body>Local help</body></html>
                """, StandardCharsets.UTF_8);

        JavadocReport mojo = new JavadocReport();
        MojoFieldInjector injector = new MojoFieldInjector();
        MavenProject project = newProject(projectDirectory, sourceDirectory, resourcesDirectory, classesDirectory);
        MavenProject module = newModuleProject(projectDirectory, outputDirectory);
        Path localRepositoryDirectory = projectDirectory.resolve("local-repository");
        Files.createDirectories(localRepositoryDirectory);

        injector.inject(mojo, "project", project);
        injector.inject(mojo, "reactorProjects", Arrays.asList(project, module));
        injector.inject(mojo, "localRepository", localRepository(localRepositoryDirectory));
        injector.inject(mojo, "remoteRepositories", Collections.emptyList());
        mojo.setReportOutputDirectory(outputDirectory.toFile());
        injector.inject(mojo, "javadocOptionsDir", javadocOptionsDirectory.toFile());
        injector.inject(mojo, "javadocDirectory", javadocDirectory.toFile());
        injector.inject(mojo, "javadocExecutable", createJavadocExecutable(projectDirectory).toString());
        injector.inject(mojo, "stylesheet", "maven");
        injector.inject(mojo, "helpfile", resourcesDirectory.resolve("docs/help.html").toString());
        injector.inject(mojo, "source", "8");
        injector.inject(mojo, "encoding", "UTF-8");
        injector.inject(mojo, "docencoding", "UTF-8");
        injector.inject(mojo, "charset", "UTF-8");
        injector.inject(mojo, "useStandardDocletOptions", true);
        injector.inject(mojo, "detectJavaApiLink", true);
        injector.inject(mojo, "detectOfflineLinks", true);
        injector.inject(mojo, "detectLinks", false);
        injector.inject(mojo, "failOnError", true);
        injector.inject(mojo, "isOffline", true);

        String previousMavenHome = System.getProperty("maven.home");
        System.setProperty("maven.home", "");
        try {
            mojo.generate(null, Locale.ENGLISH);
        } finally {
            if (previousMavenHome == null) {
                System.clearProperty("maven.home");
            } else {
                System.setProperty("maven.home", previousMavenHome);
            }
        }

        assertTrue(Files.isRegularFile(outputDirectory.resolve("stylesheet.css")));
        assertTrue(Files.readString(outputDirectory.resolve("index.html"), StandardCharsets.UTF_8)
                .contains("function validURL(url) {"));
        assertTrue(Files.isRegularFile(javadocOptionsDirectory.resolve("javadoc-options-javadoc-resources.xml")));
    }

    private static MavenProject newProject(Path projectDirectory, Path sourceDirectory, Path resourcesDirectory,
            Path classesDirectory) throws IOException {
        Files.createDirectories(projectDirectory.resolve("target"));
        Model model = baseModel("root");
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        build.setSourceDirectory(sourceDirectory.toString());
        build.setOutputDirectory(classesDirectory.toString());
        Resource resource = new Resource();
        resource.setDirectory(resourcesDirectory.toString());
        build.addResource(resource);
        model.setBuild(build);

        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Files.writeString(project.getFile().toPath(), "<project/>", StandardCharsets.UTF_8);
        project.addCompileSourceRoot(sourceDirectory.toString());
        project.setArtifact(newArtifact("example", "root"));
        Artifact moduleArtifact = newArtifact("example", "module");
        project.setDependencyArtifacts(Collections.singleton(moduleArtifact));
        project.setArtifacts(Collections.emptySet());
        project.setExecutionRoot(true);
        return project;
    }

    private static MavenProject newModuleProject(Path rootDirectory, Path rootOutputDirectory) throws IOException {
        Path moduleDirectory = rootDirectory.resolve("module");
        Files.createDirectories(moduleDirectory);
        Model model = baseModel("module");
        Build build = new Build();
        build.setDirectory(moduleDirectory.resolve("target").toString());
        build.setSourceDirectory(moduleDirectory.resolve("src/main/java").toString());
        build.setOutputDirectory(moduleDirectory.resolve("target/classes").toString());
        model.setBuild(build);

        MavenProject project = new MavenProject(model);
        project.setFile(moduleDirectory.resolve("pom.xml").toFile());
        Files.writeString(project.getFile().toPath(), "<project/>", StandardCharsets.UTF_8);
        project.setArtifact(newArtifact("example", "module"));
        project.setDependencyArtifacts(Collections.emptySet());
        project.setArtifacts(Collections.emptySet());
        project.setUrl("https://example.invalid/module");
        Path moduleJavadocDirectory = moduleDirectory.resolve(rootDirectory.relativize(rootOutputDirectory));
        Files.deleteIfExists(moduleJavadocDirectory.resolve("package-list"));
        return project;
    }

    private static Model baseModel(String artifactId) {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("example");
        model.setArtifactId(artifactId);
        model.setVersion("1.0");
        model.setPackaging("jar");
        model.setName(artifactId);
        model.setUrl("https://example.invalid/" + artifactId);
        return model;
    }

    private static Artifact newArtifact(String groupId, String artifactId) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");
        return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion("1.0"), "compile", "jar", null,
                handler);
    }

    private static ArtifactRepository localRepository(Path localRepositoryDirectory) {
        return new DefaultArtifactRepository("local", localRepositoryDirectory.toUri().toString(),
                new DefaultRepositoryLayout());
    }

    private static Path createJavadocExecutable(Path directory) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path executable = directory.resolve(windows ? "javadoc.cmd" : "javadoc");
        String script = windows ? windowsJavadocScript() : unixJavadocScript();
        Files.writeString(executable, script, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true);
        return executable;
    }

    private static String unixJavadocScript() {
        return """
                #!/bin/sh
                for arg in "$@"; do
                  if [ "$arg" = "-J-version" ]; then
                    echo 'java full version "1.8.0_999"' >&2
                    exit 0
                  fi
                done
                cat > index.html <<'EOF'
                <html><head><script type="text/javascript">
                function loadFrames() {
                  return true;
                }
                </script></head><body>Generated</body></html>
                EOF
                exit 0
                """;
    }

    private static String windowsJavadocScript() {
        return """
                @echo off
                for %%A in (%*) do if "%%~A"=="-J-version" (
                  echo java full version "1.8.0_999" 1>&2
                  exit /b 0
                )
                > index.html echo ^<html^>^<head^>^<script type="text/javascript"^>
                >> index.html echo function loadFrames() {
                >> index.html echo   return true;
                >> index.html echo }
                >> index.html echo ^</script^>^</head^>^<body^>Generated^</body^>^</html^>
                exit /b 0
                """;
    }

    static final class MojoFieldInjector extends AbstractMojoTestCase {
        void inject(Object target, String name, Object value) throws IllegalAccessException {
            setVariableValueToObject(target, name, value);
        }
    }
}
