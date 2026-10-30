/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JarUtilsTests {

    @TempDir
    Path tempDir;

    @Test
    void mainJarExcludesClassifiedAndNonJarArtifacts() {
        assertThat(JarUtils.isMainJar("jar", null, "1.0", "1.0")).isTrue();
        assertThat(JarUtils.isMainJar("jar", "", "1.0", "1.0")).isTrue();
        assertThat(JarUtils.isMainJar("jar", "test", "1.0", "1.0")).isFalse();
        assertThat(JarUtils.isMainJar("jar", "sources", "1.0", "1.0")).isFalse();
        assertThat(JarUtils.isMainJar("pom", null, "1.0", "1.0")).isFalse();
    }

    @Test
    void mainJarOfVariantCoordinateIsClassifiedAsTheVersionSuffix() {
        assertThat(JarUtils.isMainJar("jar", "jakarta", "3.10.8-jakarta", "3.10.8")).isTrue();
        assertThat(JarUtils.isMainJar("jar", "sources", "3.10.8-jakarta", "3.10.8")).isFalse();
        assertThat(JarUtils.isMainJar("jar", "jakarta", "3.10.8", "3.10.8")).isFalse();
        assertThat(JarUtils.isMainJar("pom", "jakarta", "3.10.8-jakarta", "3.10.8")).isFalse();
    }

    @Test
    void loadClassNamesSkipsModuleInfoAndReadsMultiReleaseEntries() throws IOException {
        Path jar = createLibraryJar(tempDir.resolve("library.jar"), List.of(
                "module-info.class",
                "com/example/Base.class",
                "META-INF/versions/11/com/example/Versioned.class"
        ));

        assertThat(JarUtils.loadClassNames(List.of(jar)))
                .containsExactlyInAnyOrder("com.example.Base", "com.example.Versioned");
    }

    @Test
    void extractEffectiveClassesRejectsEntriesOutsideTheOutputDirectory() throws IOException {
        Path jar = createLibraryJar(tempDir.resolve("library.jar"), List.of("../Escaped.class"));
        Path outputDir = tempDir.resolve("effective");

        assertThatThrownBy(() -> JarUtils.extractEffectiveClasses(jar, 25, outputDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("../Escaped.class");
        assertThat(tempDir.resolve("Escaped.class")).doesNotExist();
    }

    @Test
    void extractPackagesSkipsDefaultPackage() {
        assertThat(JarUtils.extractPackages(Set.of(
                "PlainClass",
                "com.example.Foo",
                "com.example.bar.Baz"
        ))).containsExactly("com.example", "com.example.bar");
    }

    @Test
    void derivePackageRootsCollapsesSiblingPackagesToParent() {
        assertThat(JarUtils.derivePackageRoots(Set.of(
                "net.jpountz.lz4.LZ4Factory",
                "net.jpountz.util.SafeUtils"
        ))).containsExactly("net.jpountz");
    }

    @Test
    void derivePackageRootsDoesNotCollapseToSingleSegmentParent() {
        assertThat(JarUtils.derivePackageRoots(Set.of(
                "org.foo.alpha.Alpha",
                "org.bar.beta.Beta"
        ))).containsExactly("org.bar.beta", "org.foo.alpha");
    }

    private Path createLibraryJar(Path jarPath, List<String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String entry : entries) {
                jarOutputStream.putNextEntry(new JarEntry(entry));
                jarOutputStream.write(new byte[]{0});
                jarOutputStream.closeEntry();
            }
        }
        return jarPath;
    }
}
