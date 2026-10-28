/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BaseLayerUtilsTests {
    @TempDir
    Path tempDir;

    @Test
    void dedicatedLayerPathIsStableAndCoordinateSpecific() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        project.getExtensions().getExtraProperties().set("tck.dedicatedLayerRoot", tempDir.toString());

        File first = BaseLayerUtils.resolveDedicatedLayerFile(project, "com.example:demo:1.0");
        File repeated = BaseLayerUtils.resolveDedicatedLayerFile(project, "com.example:demo:1.0");
        File other = BaseLayerUtils.resolveDedicatedLayerFile(project, "com.example:demo:2.0");

        assertThat(first).isEqualTo(repeated);
        assertThat(first).isNotEqualTo(other);
        assertThat(first.getName()).isEqualTo("libDedicated.nil");
        assertThat(first.getParentFile().getName()).hasSize(64);
    }

    @Test
    void identifiesJUnitRuntimeArtifactGroups() {
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("junit", "junit")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.junit.jupiter", "junit-jupiter-api")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.junit.platform", "junit-platform-engine")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.junit.vintage", "junit-vintage-engine")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.opentest4j", "opentest4j")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.apiguardian", "apiguardian-api")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.hamcrest", "hamcrest-core")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact(
                "org.graalvm.buildtools", "junit-platform-native")).isTrue();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact(
                "org.graalvm.buildtools", "native-gradle-plugin")).isFalse();
        assertThat(BaseLayerUtils.isJUnitRuntimeArtifact("org.assertj", "assertj-core")).isFalse();
    }

    @Test
    void copiesOnlyBaseDynamicLibrariesIntoTheImageDirectory() throws IOException {
        Path base = Files.createDirectories(tempDir.resolve("base"));
        Path image = tempDir.resolve("image");
        List<String> libraries = List.of("libbase.so", "libawt.so", "libfontmanager.dylib", "jawt.dll");
        for (String library : libraries) {
            Files.writeString(base.resolve(library), library);
        }
        Files.writeString(base.resolve("base.nil"), "layer archive");
        Files.writeString(base.resolve("manifest.json"), "{}");
        Files.writeString(base.resolve("other.txt"), "not a library");
        Files.createDirectory(base.resolve("directory.so"));

        BaseLayerUtils.copyDynamicLibraries(base.resolve("base.nil").toFile(), image.toFile());

        try (Stream<Path> files = Files.list(image)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrderElementsOf(libraries);
        }
        for (String library : libraries) {
            assertThat(Files.readString(image.resolve(library))).isEqualTo(library);
            assertThat(Files.readString(base.resolve(library))).isEqualTo(library);
        }
    }

    @Test
    void preservesApplicationLibrariesAndExistingImageFiles() throws IOException {
        Path base = Files.createDirectories(tempDir.resolve("base"));
        Path image = Files.createDirectories(tempDir.resolve("image"));
        Files.writeString(base.resolve("libjvm.so"), "base shim");
        Files.writeString(base.resolve("libawt.so"), "base AWT");
        Files.writeString(image.resolve("libjvm.so"), "application shim");
        Files.writeString(image.resolve("test-binary"), "application executable");

        BaseLayerUtils.copyDynamicLibraries(base.resolve("base.nil").toFile(), image.toFile());
        BaseLayerUtils.copyDynamicLibraries(base.resolve("base.nil").toFile(), image.toFile());

        assertThat(Files.readString(image.resolve("libjvm.so"))).isEqualTo("application shim");
        assertThat(Files.readString(image.resolve("libawt.so"))).isEqualTo("base AWT");
        assertThat(Files.readString(image.resolve("test-binary"))).isEqualTo("application executable");
    }
}
