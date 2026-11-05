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
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.ModuleDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

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
    void extractEffectiveClassesSkipsClassesStoredUnderAForeignPrefix() throws IOException {
        byte[] base = classNamed("com/example/Foo", "base");
        byte[] overlay = classNamed("com/example/Foo", "java11");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("module-info.class",
                ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of("com.example"), module -> { })));
        entries.put("com/example/package-info.class", classNamed("com/example/package-info", "info"));
        entries.put("com/example/Foo.class", base);
        entries.put("1.0/com/example/Foo.class", classNamed("com/example/Foo", "legacy"));
        entries.put("META-INF/versions/11/com/example/Foo.class", overlay);
        entries.put("META-INF/versions/11/1.0/com/example/Foo.class", classNamed("com/example/Foo", "legacy11"));
        entries.put("com/example/Unparseable.class", new byte[]{0});
        Path jar = createJar(tempDir.resolve("library.jar"), entries);
        Path outputDir = tempDir.resolve("effective");

        JarUtils.extractEffectiveClasses(jar, 25, outputDir);

        try (Stream<Path> files = Files.walk(outputDir)) {
            assertThat(files.filter(Files::isRegularFile).map(file -> outputDir.relativize(file).toString()))
                    .containsExactlyInAnyOrder(
                            "module-info.class",
                            "com/example/package-info.class",
                            "com/example/Foo.class",
                            "com/example/Unparseable.class"
                    );
        }
        assertThat(outputDir.resolve("com/example/Foo.class")).hasBinaryContent(overlay);
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
        Map<String, byte[]> contents = new LinkedHashMap<>();
        entries.forEach(entry -> contents.put(entry, new byte[]{0}));
        return createJar(jarPath, contents);
    }

    private Path createJar(Path jarPath, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
                jarOutputStream.write(entry.getValue());
                jarOutputStream.closeEntry();
            }
        }
        return jarPath;
    }

    /// Returns a class file declaring `internalName`, made distinct from its namesakes by `field`.
    private static byte[] classNamed(String internalName, String field) {
        return ClassFile.of().build(ClassDesc.ofInternalName(internalName),
                builder -> builder.withField(field, ConstantDescs.CD_int, ClassFile.ACC_PUBLIC));
    }
}
