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

class JarUtilsTests {

    @TempDir
    Path tempDir;

    @Test
    void unclassifiedMainJarExcludesClassifiedAndNonJarArtifacts() {
        assertThat(JarUtils.isUnclassifiedMainJar("jar", null)).isTrue();
        assertThat(JarUtils.isUnclassifiedMainJar("jar", "")).isTrue();
        assertThat(JarUtils.isUnclassifiedMainJar("jar", "test")).isFalse();
        assertThat(JarUtils.isUnclassifiedMainJar("jar", "sources")).isFalse();
        assertThat(JarUtils.isUnclassifiedMainJar("pom", null)).isFalse();
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
