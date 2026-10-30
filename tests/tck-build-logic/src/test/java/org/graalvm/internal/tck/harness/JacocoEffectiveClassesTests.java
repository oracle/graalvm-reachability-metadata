/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/// §AR-test-harness.8: the JaCoCo reports analyze the classes the JVM loads from the library's
/// main JAR, whatever timestamps its entries carry.
class JacocoEffectiveClassesTests {

    /// 1969-12-31T23:00:00Z, the stamp on every entry of `simulacrum-scalafix-annotations_3:0.5.4`.
    private static final long BEFORE_EPOCH = -3_600_000L;

    private static final Map<String, String> LIBRARY_ENTRIES = Map.of(
            "com/example/Demo.class", "base",
            "com/example/Versioned.class", "base",
            "META-INF/versions/9/com/example/Versioned.class", "java9",
            "META-INF/versions/11/com/example/Versioned.class", "java11",
            "META-INF/versions/9999/com/example/Versioned.class", "future",
            "com/example/notes.txt", "resource"
    );

    @TempDir
    Path repoRoot;

    @Test
    void extractsEffectiveClassesOfJarStampedBeforeEpoch() throws IOException {
        Path libraryJar = writeFixture();
        try (ZipFile zipFile = new ZipFile(libraryJar.toFile())) {
            assertThat(Collections.list(zipFile.entries())).extracting(ZipEntry::getTime).containsOnly(BEFORE_EPOCH);
        }

        GradleRunner.create()
                .withProjectDir(projectDir().toFile())
                .withPluginClasspath()
                .withArguments("jacocoCodeCoverageReport", "-x", "test", "-x", "codeCoverageTest", "--offline")
                .build();

        Path effective = projectDir().resolve("build/jacoco/effective/demo_1_0_0_jar");
        assertThat(effective.resolve("com/example/Demo.class")).hasContent("base");
        assertThat(effective.resolve("com/example/Versioned.class")).hasContent("java11");
        assertThat(effective.resolve("com/example/notes.txt")).doesNotExist();
        assertThat(effective.resolve("META-INF")).doesNotExist();
    }

    /// Writes a minimal repository whose coordinate `com.example:demo:1.0.0` resolves from a local
    /// Maven repository, and returns the library JAR, every entry of which is dated before 1970.
    private Path writeFixture() throws IOException {
        Files.writeString(repoRoot.resolve("LICENSE"), "test");
        Files.createDirectories(repoRoot.resolve("metadata/com.example/demo/1.0.0"));
        Files.createDirectories(repoRoot.resolve("tests/tck-build-logic"));
        Files.writeString(repoRoot.resolve("ci.json"), """
                {
                  "buildArgs": [],
                  "nativeImageModes": {
                    "current-defaults": []
                  }
                }
                """);
        Files.createDirectories(repoRoot.resolve("gradle"));
        Files.writeString(repoRoot.resolve("gradle/libs.versions.toml"), """
                [versions]
                scala2 = "2.13.17"
                scala3 = "3.7.4"

                [libraries]
                test-junit-bom = { module = "org.junit:junit-bom", version = "5.9.2" }
                test-junit-jupiter-core = { module = "org.junit.jupiter:junit-jupiter" }
                test-junit-vintage = { module = "org.junit.vintage:junit-vintage-engine" }
                """);

        Path mavenRepository = repoRoot.resolve("maven-repository");
        Path libraryDir = Files.createDirectories(mavenRepository.resolve("com/example/demo/1.0.0"));
        Files.writeString(libraryDir.resolve("demo-1.0.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path libraryJar = libraryDir.resolve("demo-1.0.0.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(libraryJar))) {
            for (Map.Entry<String, String> entry : LIBRARY_ENTRIES.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(BEFORE_EPOCH);
                jar.putNextEntry(jarEntry);
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }

        Path projectDir = projectDir();
        Files.createDirectories(projectDir.resolve("build/jacoco"));
        Files.createFile(projectDir.resolve("build/jacoco/test.exec"));
        Files.writeString(projectDir.resolve("settings.gradle"), """
                dependencyResolutionManagement {
                    versionCatalogs {
                        libs {
                            from(files("../../../../../gradle/libs.versions.toml"))
                        }
                    }
                }

                rootProject.name = "com.example.demo_tests"
                """);
        Files.writeString(projectDir.resolve("gradle.properties"), """
                library.coordinates = com.example:demo:1.0.0
                library.version = 1.0.0
                metadata.dir = com.example/demo/1.0.0/
                """);
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id "org.graalvm.internal.tck"
                }

                // Resolve only the tested library, from the fixture's repository, to stay offline.
                repositories.clear()
                repositories {
                    maven { url = uri("%s") }
                }
                configurations.configureEach {
                    withDependencies { it.removeIf { dependency -> dependency.group != "com.example" } }
                }
                dependencies {
                    testImplementation "com.example:demo:1.0.0"
                }

                // Keep the harness's class extraction; JaCoCo's own report needs its tools online.
                tasks.named("jacocoCodeCoverageReport") {
                    actions.removeIf { it.displayName.startsWith("Execute generate") }
                }
                """.formatted(mavenRepository.toUri()));
        return libraryJar;
    }

    private Path projectDir() {
        return repoRoot.resolve("tests/src/com.example/demo/1.0.0");
    }
}
