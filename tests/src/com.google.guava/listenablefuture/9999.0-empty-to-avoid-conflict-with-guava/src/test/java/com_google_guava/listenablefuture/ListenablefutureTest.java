/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.listenablefuture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListenablefutureTest {

    @Test
    void placeholderArtifactDoesNotPublishListenableFutureTypes() {
        assertThat(classResource("com/google/common/util/concurrent/ListenableFuture.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/AbstractFuture.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/Futures.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/SettableFuture.class")).isNull();
    }

    @Test
    void placeholderArtifactDoesNotPublishCoreGuavaTypes() {
        assertThat(classResource("com/google/common/base/Optional.class")).isNull();
        assertThat(classResource("com/google/common/collect/ImmutableList.class")).isNull();
        assertThat(classResource("com/google/common/hash/Hashing.class")).isNull();
        assertThat(classResource("com/google/common/io/Files.class")).isNull();
    }

    @Test
    void placeholderArtifactDoesNotDefineCommonGuavaPackages() {
        assertThat(classResource("com/google/common/base/package-info.class")).isNull();
        assertThat(classResource("com/google/common/collect/package-info.class")).isNull();
        assertThat(classResource("com/google/common/hash/package-info.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/package-info.class")).isNull();
    }

    @Test
    void placeholderArtifactPublishesMavenCoordinatesMetadata() throws IOException {
        Properties properties = new Properties();
        try (JarFile jarFile = new JarFile(placeholderArtifactJarPath().toFile());
             InputStream inputStream = jarEntryStream(jarFile, "META-INF/maven/com.google.guava/listenablefuture/pom.properties")) {
            properties.load(inputStream);
        }

        assertThat(properties)
                .containsEntry("groupId", "com.google.guava")
                .containsEntry("artifactId", "listenablefuture");
        assertThat(properties.getProperty("version")).isNotBlank();
    }

    @Test
    void placeholderArtifactPomExplainsItsConflictAvoidancePurpose() throws IOException {
        String pomXml;
        try (JarFile jarFile = new JarFile(placeholderArtifactJarPath().toFile());
             InputStream inputStream = jarEntryStream(jarFile, "META-INF/maven/com.google.guava/listenablefuture/pom.xml")) {
            pomXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String normalizedPomXml = pomXml.replaceAll("\\s+", " ");

        assertThat(normalizedPomXml)
                .contains("<artifactId>listenablefuture</artifactId>")
                .contains("<name>Guava ListenableFuture only</name>")
                .contains("An empty artifact")
                .contains("signal that it is providing ListenableFuture")
                .contains("conflict with the copy of ListenableFuture in guava itself");
    }

    @Test
    void placeholderArtifactContainsOnlyMetadataFiles() throws IOException {
        List<String> fileEntries;
        try (JarFile jarFile = new JarFile(placeholderArtifactJarPath().toFile())) {
            fileEntries = jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(JarEntry::getName)
                    .toList();
        }

        assertThat(fileEntries)
                .containsExactly(
                        "META-INF/MANIFEST.MF",
                        "META-INF/maven/com.google.guava/listenablefuture/pom.xml",
                        "META-INF/maven/com.google.guava/listenablefuture/pom.properties");
    }

    private java.net.URL classResource(final String resourcePath) {
        return getClass().getClassLoader().getResource(resourcePath);
    }

    private InputStream jarEntryStream(final JarFile jarFile, final String entryName) throws IOException {
        return jarFile.getInputStream(Objects.requireNonNull(jarFile.getJarEntry(entryName), entryName + " not found"));
    }

    private Path placeholderArtifactJarPath() throws IOException {
        Path artifactCacheDirectory = Path.of(
                System.getProperty("user.home"),
                ".gradle",
                "caches",
                "modules-2",
                "files-2.1",
                "com.google.guava",
                "listenablefuture");

        try (Stream<Path> paths = Files.find(
                artifactCacheDirectory,
                4,
                (path, attributes) -> attributes.isRegularFile() && path.getFileName().toString().endsWith(".jar"))) {
            return paths.findFirst().orElseThrow(() -> new IllegalStateException("Placeholder artifact jar not found"));
        }
    }
}
