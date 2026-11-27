/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Duplicate classes must resolve identically in the base and application. §FS-tests.3.7
class DedicatedLayerClasspathTests {
    @TempDir
    Path tempDir;

    @Test
    void preservesDependencyPrecedenceAndKeepsTestOutputsOutOfTheBase() throws IOException {
        Map<String, Object> result = inspect("");

        assertThat(result.get("baseMarker")).isEqualTo("new");
        assertThat(result.get("testMarker")).isEqualTo("new");
        assertThat(files(result, "base")).containsSubsequence("z-new-1.0.jar", "a-old-1.0.jar")
                .doesNotContain("test-fixture.jar", "test-classes", "test-resources");
        assertThat(files(result, "test")).contains("test-fixture.jar", "test-classes", "test-resources");
    }

    @Test
    void followsTheConfiguredNativeTestClasspathOrder() throws IOException {
        Map<String, Object> result = inspect("""
                graalvmNative.binaries.test.classpath.setFrom(
                    files('repository/fixtures/a-old/1.0/a-old-1.0.jar'),
                    configurations.nativeImageTestClasspath)
                """);

        assertThat(result.get("baseMarker")).isEqualTo("old");
        assertThat(result.get("testMarker")).isEqualTo("old");
        assertThat(files(result, "base")).containsSubsequence("a-old-1.0.jar", "z-new-1.0.jar");
    }

    @Test
    void doesNotReintroduceDependenciesRemovedFromTheNativeTestClasspath() throws IOException {
        Map<String, Object> result = inspect("""
                graalvmNative.binaries.test.classpath.setFrom(provider {
                    configurations.nativeImageTestClasspath.files.findAll { it.name != 'a-old-1.0.jar' }
                })
                """);

        assertThat(files(result, "base")).contains("z-new-1.0.jar").doesNotContain("a-old-1.0.jar");
        assertThat(result.get("baseMarker")).isEqualTo(result.get("testMarker"));
    }

    @Test
    void doesNotTransformTheUnbuiltApplicationJar() throws IOException {
        Path project = fixture("""
                group = 'fixtures'
                def transformed = Attribute.of('fixture.transformed', Boolean)
                dependencies.artifactTypes.getByName('jar').attributes.attribute(transformed, false)
                dependencies.registerTransform(CopyFixtureJar) {
                    from.attribute(Attribute.of('artifactType', String), 'jar').attribute(transformed, false)
                    to.attribute(Attribute.of('artifactType', String), 'jar').attribute(transformed, true)
                }
                configurations.nativeImageTestClasspath.attributes.attribute(transformed, true)
                abstract class CopyFixtureJar implements TransformAction<TransformParameters.None> {
                    @InputArtifact
                    @PathSensitive(PathSensitivity.NAME_ONLY)
                    abstract Provider<FileSystemLocation> getInputArtifact()
                    void transform(TransformOutputs outputs) {
                        File input = inputArtifact.get().asFile
                        Files.copy(input.toPath(), outputs.file(input.name).toPath())
                    }
                }
                tasks.register('inspectBaseOnly') {
                    doLast {
                        assert !tasks.named('jar').get().archiveFile.get().asFile.exists()
                        assert graalvmNative.binaries.libDedicated.classpath.files*.name.containsAll(
                            ['z-new-1.0.jar', 'a-old-1.0.jar'])
                    }
                }
                """);
        Path buildFile = project.resolve("build.gradle");
        Files.writeString(buildFile, """
                import java.nio.file.Files
                import org.gradle.api.artifacts.transform.*
                import org.gradle.api.attributes.Attribute
                import org.gradle.api.file.FileSystemLocation
                import org.gradle.api.provider.Provider
                import org.gradle.api.tasks.PathSensitive
                import org.gradle.api.tasks.PathSensitivity
                """ + Files.readString(buildFile));

        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("inspectBaseOnly", "--offline", "--stacktrace").build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> inspect(String customization) throws IOException {
        Path project = fixture(customization);
        String output = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("inspectLayerClasspaths", "--offline", "--quiet").build().getOutput();
        String json = output.lines().filter(line -> line.startsWith("classpaths="))
                .findFirst().orElseThrow().substring("classpaths=".length());
        return (Map<String, Object>) new JsonSlurper().parseText(json);
    }

    private Path fixture(String customization) throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, "", true);
        publish(project, "a-old", "old", "");
        publish(project, "z-new", "new", "private String relationship; private boolean ignoringCase;");
        Path buildFile = project.resolve("build.gradle");
        Files.writeString(buildFile, "import java.util.jar.JarOutputStream\n" + Files.readString(buildFile));
        Files.writeString(project.resolve("build.gradle"), """

                // This fixture resolves only its local duplicate-class dependencies.
                configurations.configureEach {
                    withDependencies { dependencies -> dependencies.removeAll { it.group != 'fixtures' } }
                }
                repositories { maven { url = uri('repository') } }
                dependencies {
                    testImplementation 'fixtures:z-new:1.0'
                    testImplementation 'fixtures:a-old:1.0'
                }
                %s
                File testJar = file('build/test-fixture.jar')
                testJar.parentFile.mkdirs()
                new JarOutputStream(new FileOutputStream(testJar)).close()
                File classes = file('build/test-classes')
                File resources = file('build/test-resources')
                classes.mkdirs()
                resources.mkdirs()
                graalvmNative.binaries.test.classpath.from(testJar, classes, resources)

                String marker(List<File> classpath) {
                    URL[] urls = classpath.collect { it.toURI().toURL() } as URL[]
                    new URLClassLoader(urls, (ClassLoader) null).withCloseable { loader ->
                        loader.loadClass('example.Duplicate').getMethod('marker').invoke(null)
                    }
                }
                tasks.register('inspectLayerClasspaths') {
                    doLast {
                        List<File> base = graalvmNative.binaries.libDedicated.classpath.files.toList()
                        List<File> application = graalvmNative.binaries.test.classpath.files.toList()
                        println 'classpaths=' + JsonOutput.toJson([
                            base: base.collect { it.name }, test: application.collect { it.name },
                            baseMarker: marker(base), testMarker: marker(application)
                        ])
                    }
                }
                """.formatted(customization), StandardOpenOption.APPEND);

        return project;
    }

    @SuppressWarnings("unchecked")
    private List<String> files(Map<String, Object> result, String layer) {
        return (List<String>) result.get(layer);
    }

    private void publish(Path project, String artifact, String marker, String extraFields) throws IOException {
        Path directory = Files.createDirectories(project.resolve("repository/fixtures/" + artifact + "/1.0"));
        Path source = directory.resolve("Duplicate.java");
        Files.writeString(source, """
                package example;
                public class Duplicate {
                    %s
                    private String substring;
                    public static String marker() { return "%s"; }
                }
                """.formatted(extraFields, marker));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        assertThat(compiler.run(null, null, null, "-d", classes.toString(), source.toString())).isZero();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(directory.resolve(artifact + "-1.0.jar")));
             Stream<Path> entries = Files.walk(classes)) {
            for (Path entry : entries.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(entry).toString().replace('\\', '/')));
                Files.copy(entry, jar);
                jar.closeEntry();
            }
        }
        Files.writeString(directory.resolve(artifact + "-1.0.pom"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>fixtures</groupId><artifactId>%s</artifactId><version>1.0</version>
                </project>
                """.formatted(artifact));
    }
}
