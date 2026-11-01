/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

/// Validates exact overlap selection against the builder JDK's module inventory. §FS-tests.3.4
class DedicatedLayerModulesTests {
    @TempDir
    Path tempDir;
    private Path javaHome;

    @BeforeEach
    void createJdkInventory() throws IOException {
        javaHome = tempDir.resolve("jdk");
        Files.createDirectories(javaHome.resolve("jmods"));
        for (String module : BaseLayerUtils.layerModules()) {
            writeArchive(javaHome.resolve("jmods/" + module + ".jmod"), List.of());
        }
        module("java.xml", List.of("javax/xml/stream/XMLStreamWriter.class"));
        module("java.transaction.xa", List.of("javax/transaction/xa/Xid.class"));
        module("jdk.xml.dom", List.of("org/w3c/dom/css/CSSRule.class"));
    }

    @Test
    void omitsEveryConflictingOptionalModuleAndPreservesJars() throws IOException {
        Path jar = writeArchive(tempDir.resolve("api.jar"), List.of(
                "javax/xml/stream/XMLStreamWriter.class", "javax/transaction/xa/Xid.class",
                "org/w3c/dom/css/CSSRule.class", "com/example/Implementation.class"));
        byte[] original = Files.readAllBytes(jar);

        assertThat(BaseLayerUtils.dedicatedLayerModules(List.of(jar), javaHome))
                .containsExactlyElementsOf(BaseLayerUtils.layerModules().stream()
                        .filter(module -> !List.of("java.xml", "java.transaction.xa", "jdk.xml.dom").contains(module))
                        .toList());
        assertThat(Files.readAllBytes(jar)).isEqualTo(original);
    }

    @Test
    void considersDependencyJarsBeyondTheSelectedLibrary() throws IOException {
        Path library = writeArchive(tempDir.resolve("library.jar"), List.of("com/example/Library.class"));
        Path dependency = writeArchive(tempDir.resolve("dependency.jar"),
                List.of("javax/transaction/xa/Xid.class"));

        assertThat(BaseLayerUtils.dedicatedLayerModules(List.of(library, dependency), javaHome))
                .doesNotContain("java.transaction.xa").contains("java.xml", "jdk.xml.dom", "java.base");
    }

    @Test
    void keepsJavaBaseEvenWhenItsClassesOverlap() throws IOException {
        module("java.base", List.of("java/util/concurrent/ExecutorService.class"));
        Path jar = writeArchive(tempDir.resolve("base-api.jar"),
                List.of("java/util/concurrent/ExecutorService.class"));

        assertThat(BaseLayerUtils.dedicatedLayerModules(List.of(jar), javaHome))
                .containsExactlyElementsOf(BaseLayerUtils.layerModules());
    }

    @Test
    void matchesClassesRatherThanPackagesAndIgnoresModuleDescriptors() throws IOException {
        Path jar = writeArchive(tempDir.resolve("unrelated.jar"), List.of(
                "javax/xml/stream/CustomWriter.class", "module-info.class",
                "META-INF/versions/9/module-info.class"));

        assertThat(BaseLayerUtils.dedicatedLayerModules(List.of(jar), javaHome))
                .containsExactlyElementsOf(BaseLayerUtils.layerModules());
    }

    @Test
    void detectsVersionedClassEntriesLikeTheNativeImageScanner() throws IOException {
        Path jar = writeArchive(tempDir.resolve("versioned.jar"), List.of(
                "META-INF/versions/9/javax/xml/stream/XMLStreamWriter.class",
                "META-INF/versions/11/org/w3c/dom/css/CSSRule.class"));

        assertThat(BaseLayerUtils.dedicatedLayerModules(List.of(jar), javaHome))
                .doesNotContain("java.xml", "jdk.xml.dom").contains("java.transaction.xa");
    }

    @Test
    void usesTheSuppliedJdkInventoryRatherThanTheGradleRuntime() throws IOException {
        module("java.xml", List.of("com/example/TargetJdkApi.class"));
        Path jar = writeArchive(tempDir.resolve("target-api.jar"), List.of("com/example/TargetJdkApi.class"));

        assertThat(BaseLayerUtils.dedicatedLayerModules(List.of(jar), javaHome)).doesNotContain("java.xml");
    }

    @Test
    void failsWhenTheBuilderModuleCannotBeInspected() throws IOException {
        Path jar = writeArchive(tempDir.resolve("api.jar"), List.of("com/example/Library.class"));
        Files.delete(javaHome.resolve("jmods/java.xml.jmod"));

        assertThatIOException().isThrownBy(() -> BaseLayerUtils.dedicatedLayerModules(List.of(jar), javaHome))
                .withMessageContaining("java.xml.jmod");
    }

    private void module(String name, List<String> classes) throws IOException {
        writeArchive(javaHome.resolve("jmods/" + name + ".jmod"),
                classes.stream().map(entry -> "classes/" + entry).toList());
    }

    private Path writeArchive(Path path, List<String> entries) throws IOException {
        try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                archive.putNextEntry(new ZipEntry(entry));
                archive.write(new byte[]{0});
                archive.closeEntry();
            }
        }
        return path;
    }
}
