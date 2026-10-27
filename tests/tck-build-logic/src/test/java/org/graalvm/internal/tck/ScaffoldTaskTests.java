/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.type.TypeReference;
import org.graalvm.internal.tck.model.DiscoveredArtifactMetadata;
import org.graalvm.internal.tck.model.LibraryLanguage;
import org.gradle.api.Project;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTaskTests extends ScaffoldTaskTestSupport {
    @Test
    void runCreatesCompleteScaffoldFromTemplates() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.lz4:lz4-java:1.8.0");
        installLibraryArtifact(coordinates, List.of(
                "net/jpountz/lz4/LZ4Factory.class",
                "net/jpountz/util/SafeUtils.class",
                "META-INF/versions/11/net/jpountz/lz4/LZ4FrameInputStream.class",
                "module-info.class"
        ));
        Project project = createProject();
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(listGeneratedFiles()).containsExactly(
                "metadata/org.lz4/lz4-java/1.8.0/reachability-metadata.json",
                "metadata/org.lz4/lz4-java/index.json",
                "tests/src/org.lz4/lz4-java/1.8.0/.gitignore",
                "tests/src/org.lz4/lz4-java/1.8.0/build.gradle",
                "tests/src/org.lz4/lz4-java/1.8.0/gradle.properties",
                "tests/src/org.lz4/lz4-java/1.8.0/settings.gradle",
                "tests/src/org.lz4/lz4-java/1.8.0/src/test/java/org_lz4/lz4_java/Lz4_javaTest.java",
                "tests/src/org.lz4/lz4-java/1.8.0/user-code-filter.json"
        );

        assertGeneratedMetadataIndex(
                "metadata/org.lz4/lz4-java/index.json",
                List.of("net.jpountz"),
                List.of("1.8.0")
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "metadata/org.lz4/lz4-java/1.8.0/reachability-metadata.json",
                "/scaffold/reachability-metadata.json.template"
        );
        assertGeneratedReachabilityMetadataIsEmptyJsonObject(
                "metadata/org.lz4/lz4-java/1.8.0/reachability-metadata.json"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/.gitignore",
                "/scaffold/.gitignore.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/build.gradle",
                "/scaffold/build.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/gradle.properties",
                "/scaffold/gradle.properties.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/settings.gradle",
                "/scaffold/settings.gradle.template"
        );
        assertGeneratedUserCodeFilter(
                "tests/src/org.lz4/lz4-java/1.8.0/user-code-filter.json",
                List.of("net.jpountz")
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/src/test/java/org_lz4/lz4_java/Lz4_javaTest.java",
                "/scaffold/Test.java.template"
        );
    }

    @Test
    void runSelectsStandardJvmRuntimeJarFromMultiVariantGradleModule() throws IOException {
        Coordinates coordinates = Coordinates.parse("com.example:multi-variant:1.0.0-jre");
        installJvmAndAndroidVariantArtifacts(
                coordinates,
                "1.0.0-android",
                List.of("com/example/jre/Selected.class"),
                List.of("com/example/android/Wrong.class")
        );
        Project project = createProject();
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertGeneratedMetadataIndex(
                "metadata/com.example/multi-variant/index.json",
                List.of("com.example.jre"),
                List.of("1.0.0-jre")
        );
        assertGeneratedUserCodeFilter(
                "tests/src/com.example/multi-variant/1.0.0-jre/user-code-filter.json",
                List.of("com.example.jre")
        );
    }

    @Test
    void runRejectsNotForNativeImageMarker() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.scala-js:scalajs-library_2.13:1.18.2");
        Files.createDirectories(tempDir.resolve("metadata/org.scala-js/scalajs-library_2.13"));
        Files.writeString(
                tempDir.resolve("metadata/org.scala-js/scalajs-library_2.13/index.json"),
                """
                [
                  {
                    "not-for-native-image": true,
                    "reason": "Scala.js artifact; not a JVM library consumed by native-image."
                  }
                ]
                """
        );
        Project project = createProject();
        ScaffoldTask task = registerScaffoldTask(project, "scaffoldMarker", coordinates);

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not-for-native-image");
    }

    @Test
    void runWithSkipTestsOmitsOnlyJavaTestStub() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        installLibraryArtifact(coordinates, List.of(
                "org/postgresql/Driver.class",
                "org/postgresql/util/PGobject.class"
        ));
        Project project = createProject();
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);
        task.setSkipTests(true);

        task.run();

        assertThat(listGeneratedFiles()).containsExactly(
                "metadata/org.postgresql/postgresql/42.7.3/reachability-metadata.json",
                "metadata/org.postgresql/postgresql/index.json",
                "tests/src/org.postgresql/postgresql/42.7.3/.gitignore",
                "tests/src/org.postgresql/postgresql/42.7.3/build.gradle",
                "tests/src/org.postgresql/postgresql/42.7.3/gradle.properties",
                "tests/src/org.postgresql/postgresql/42.7.3/settings.gradle",
                "tests/src/org.postgresql/postgresql/42.7.3/user-code-filter.json"
        );

        assertGeneratedUserCodeFilter(
                "tests/src/org.postgresql/postgresql/42.7.3/user-code-filter.json",
                List.of("org.postgresql")
        );
        assertThat(tempDir.resolve("tests/src/org.postgresql/postgresql/42.7.3/src/test/java/org_postgresql/postgresql/PostgresqlTest.java"))
                .doesNotExist();
    }

    @Test
    void runUsesDiscoveredKotlinScaffoldAndSeedsIndexEntry() throws IOException {
        Coordinates coordinates = Coordinates.parse("io.ktor:ktor-server-core-jvm:3.1.0");
        installLibraryArtifact(coordinates, List.of(
                "io/ktor/server/application/Application.class",
                "io/ktor/util/KtorExperimentalAPI.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                "https://example.com/source",
                "https://example.com/repository",
                "https://example.com/tests",
                "https://example.com/docs",
                "Ktor provides asynchronous servers. It is designed for Kotlin applications.",
                new LibraryLanguage("kotlin", "2.0"),
                null,
                null,
                null
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/io.ktor/ktor-server-core-jvm/3.1.0/src/test/kotlin/io_ktor/ktor_server_core_jvm/Ktor_server_core_jvmTest.kt"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/io.ktor/ktor-server-core-jvm/3.1.0/build.gradle",
                "/scaffold/build.gradle.kotlin.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/io.ktor/ktor-server-core-jvm/3.1.0/build.gradle"), StandardCharsets.UTF_8))
                .contains("alias(libs.plugins.kotlin.jvm)")
                .doesNotContain("id \"org.jetbrains.kotlin.jvm\" version")
                .contains("int testJvmVersion = tck.testJvmVersion.get()")
                .contains("jvmToolchain(testJvmVersion)")
                .contains("kotlinOptions.jvmTarget = testJvmVersion.toString()")
                .doesNotContain("jvmToolchain(21)")
                .doesNotContain("jvmToolchain(25)")
                .doesNotContain("kotlinOptions.jvmTarget = \"21\"")
                .doesNotContain("kotlinOptions.jvmTarget = \"25\"");
        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/io.ktor/ktor-server-core-jvm/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries.get(0))
                .containsEntry("source-code-url", "https://example.com/source")
                .containsEntry("repository-url", "https://example.com/repository")
                .containsEntry("test-code-url", "https://example.com/tests")
                .containsEntry("documentation-url", "https://example.com/docs")
                .containsEntry("description", "Ktor provides asynchronous servers. It is designed for Kotlin applications.")
                .containsEntry("language", Map.of("name", "kotlin", "version", "2.0"));
    }

    @Test
    void runUsesDiscoveredGroovyScaffoldAndSeedsIndexEntry() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.apache.groovy:groovy-json:4.0.21");
        installLibraryArtifact(coordinates, List.of(
                "groovy/json/JsonSlurper.class",
                "groovy/json/JsonOutput.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                "https://example.com/source",
                "https://example.com/repository",
                "https://example.com/tests",
                "https://example.com/docs",
                "Groovy JSON provides JSON parsing and generation APIs. It is designed for Groovy applications.",
                new LibraryLanguage("groovy", "4.0"),
                null,
                null,
                null
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/org.apache.groovy/groovy-json/4.0.21/src/test/groovy/org_apache_groovy/groovy_json/Groovy_jsonTest.groovy"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.apache.groovy/groovy-json/4.0.21/build.gradle",
                "/scaffold/build.gradle.groovy.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/org.apache.groovy/groovy-json/4.0.21/build.gradle"), StandardCharsets.UTF_8))
                .contains("id \"groovy\"")
                .contains("testImplementation localGroovy()")
                .contains("int testJvmVersion = tck.testJvmVersion.get()")
                .contains("JavaLanguageVersion.of(testJvmVersion)")
                .doesNotContain("JavaLanguageVersion.of(21)")
                .doesNotContain("JavaLanguageVersion.of(25)");
        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/org.apache.groovy/groovy-json/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries.get(0))
                .containsEntry("source-code-url", "https://example.com/source")
                .containsEntry("repository-url", "https://example.com/repository")
                .containsEntry("test-code-url", "https://example.com/tests")
                .containsEntry("documentation-url", "https://example.com/docs")
                .containsEntry("description", "Groovy JSON provides JSON parsing and generation APIs. It is designed for Groovy applications.")
                .containsEntry("language", Map.of("name", "groovy", "version", "4.0"));
    }

    @Test
    void runUsesDiscoveredScala3Scaffold() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.typelevel:cats-core_3:2.12.0");
        installLibraryArtifact(coordinates, List.of(
                "cats/Functor.class",
                "cats/data/Validated.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                null,
                null,
                null,
                null,
                null,
                new LibraryLanguage("scala", "3"),
                null,
                null,
                null
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/org.typelevel/cats-core_3/2.12.0/src/test/scala/org_typelevel/cats_core_3/Cats_core_3Test.scala"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.typelevel/cats-core_3/2.12.0/build.gradle",
                "/scaffold/build.gradle.scala3.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/org.typelevel/cats-core_3/2.12.0/build.gradle"), StandardCharsets.UTF_8))
                .contains("int testJvmVersion = tck.testJvmVersion.get()")
                .contains("String scala3Version = tck.scala3Version.get()")
                .contains("org.scala-lang:scala3-library_3:$scala3Version")
                .contains("org.scala-lang:scala3-compiler_3:$scala3Version")
                .contains("JavaLanguageVersion.of(testJvmVersion)")
                .doesNotContain("JavaLanguageVersion.of(21)")
                .doesNotContain("JavaLanguageVersion.of(25)")
                .doesNotContain("org.scala-lang:scala3-library_3:3.3.6")
                .doesNotContain("org.scala-lang:scala3-compiler_3:3.3.6")
                .doesNotContain("org.scala-lang:scala3-library_3:3.7.4")
                .doesNotContain("org.scala-lang:scala3-compiler_3:3.7.4");
    }

    @Test
    void runUsesDiscoveredScala2Scaffold() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.typelevel:cats-core_2.13:2.12.0");
        installLibraryArtifact(coordinates, List.of(
                "cats/Functor.class",
                "cats/data/Validated.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                null,
                null,
                null,
                null,
                null,
                new LibraryLanguage("scala", "2"),
                null,
                null,
                null
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/org.typelevel/cats-core_2.13/2.12.0/src/test/scala/org_typelevel/cats_core_2_13/Cats_core_2_13Test.scala"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.typelevel/cats-core_2.13/2.12.0/build.gradle",
                "/scaffold/build.gradle.scala2.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/org.typelevel/cats-core_2.13/2.12.0/build.gradle"), StandardCharsets.UTF_8))
                .contains("int testJvmVersion = tck.testJvmVersion.get()")
                .contains("String scala2Version = tck.scala2Version.get()")
                .contains("JavaLanguageVersion.of(testJvmVersion)")
                .contains("org.scala-lang:scala-library:$scala2Version")
                .contains("org.scala-lang:scala-compiler:$scala2Version")
                .doesNotContain("JavaLanguageVersion.of(21)")
                .doesNotContain("JavaLanguageVersion.of(25)")
                .doesNotContain("org.scala-lang:scala-library:2.13.16")
                .doesNotContain("org.scala-lang:scala-compiler:2.13.16")
                .doesNotContain("org.scala-lang:scala-library:2.13.17")
                .doesNotContain("org.scala-lang:scala-compiler:2.13.17");
    }
}
