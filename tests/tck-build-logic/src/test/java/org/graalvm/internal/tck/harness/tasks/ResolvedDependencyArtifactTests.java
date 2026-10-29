/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedDependencyArtifactTests {
    @TempDir
    Path tempDir;

    /// A library the build file declares beside the tested one is a candidate owner even when the
    /// tested library's POM omits it, as Spring Boot's POMs omit their optional dependencies.
    @Test
    void listsDependenciesTheBuildFileDeclaresBesideTheTestedLibrary() throws IOException {
        Path repository = tempDir.resolve("repository");
        publish(repository, "transitive", List.of(), "com/example/transitive/Helper.class");
        publish(repository, "lib", List.of("transitive"), "com/example/lib/Library.class");
        publish(repository, "sibling", List.of("transitive"), "org/owner/Engine.class");
        ResolvedConfiguration classpath = resolve(repository, List.of("lib", "sibling"));

        List<ResolvedDependencyArtifact> jars =
                ResolvedDependencyArtifact.runtimeClasspathJars(classpath, "com.example:lib", Set.of());

        assertThat(jars).extracting(ResolvedDependencyArtifact::coordinate).containsExactly(
                "com.example:lib:1.0.0",
                "com.example:sibling:1.0.0",
                "com.example:transitive:1.0.0"
        );
        assertThat(jars).extracting((ResolvedDependencyArtifact jar) -> jar.file().getFileName().toString())
                .containsExactly("lib-1.0.0.jar", "sibling-1.0.0.jar", "transitive-1.0.0.jar");
    }

    /// What the harness adds itself is no root, so a test framework entry traced while testing
    /// another library cannot move into the framework's metadata. The tested library's closure
    /// still reaches the same modules, and the tested library stays a root when the harness adds
    /// it too.
    @Test
    void listsHarnessModulesOnlyWhereTheTestedLibraryReachesThem() throws IOException {
        Path repository = tempDir.resolve("repository");
        publish(repository, "framework-api", List.of(), "com/example/framework/api/Api.class");
        publish(repository, "framework-engine", List.of(), "com/example/framework/engine/Engine.class");
        publish(repository, "lib", List.of("framework-api"), "com/example/lib/Library.class");
        ResolvedConfiguration classpath = resolve(repository, List.of("lib", "framework-api", "framework-engine"));
        Set<String> harnessModules = Set.of("com.example:framework-api", "com.example:framework-engine");

        assertThat(ResolvedDependencyArtifact.runtimeClasspathJars(classpath, "com.example:lib", harnessModules))
                .extracting(ResolvedDependencyArtifact::coordinate)
                .containsExactly("com.example:lib:1.0.0", "com.example:framework-api:1.0.0");
        assertThat(ResolvedDependencyArtifact.runtimeClasspathJars(classpath, "com.example:framework-engine", harnessModules))
                .extracting(ResolvedDependencyArtifact::coordinate)
                .containsExactly("com.example:framework-engine:1.0.0", "com.example:lib:1.0.0", "com.example:framework-api:1.0.0");
    }

    private ResolvedConfiguration resolve(Path repository, List<String> declaredArtifacts) {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.resolve("project").toFile()).build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getRepositories().maven(maven -> maven.setUrl(repository.toUri()));
        for (String artifact : declaredArtifacts) {
            project.getDependencies().add("testImplementation", "com.example:" + artifact + ":1.0.0");
        }
        return project.getConfigurations().getByName("testRuntimeClasspath").getResolvedConfiguration();
    }

    private void publish(Path repository, String artifact, List<String> dependencies, String classEntry)
            throws IOException {
        Path directory = repository.resolve("com/example/" + artifact + "/1.0.0");
        Files.createDirectories(directory);
        StringBuilder declarations = new StringBuilder();
        for (String dependency : dependencies) {
            declarations.append("""
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>%s</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    """.formatted(dependency));
        }
        Files.writeString(directory.resolve(artifact + "-1.0.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                %s  </dependencies>
                </project>
                """.formatted(artifact, declarations), StandardCharsets.UTF_8);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(directory.resolve(artifact + "-1.0.0.jar")))) {
            jar.putNextEntry(new ZipEntry(classEntry));
            jar.write(new byte[] {0});
            jar.closeEntry();
        }
    }
}
