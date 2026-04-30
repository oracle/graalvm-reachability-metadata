/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.smallrye.common.io.DeleteStats;
import io.smallrye.common.io.Files2;
import io.smallrye.common.io.jar.JarEntries;
import io.smallrye.common.io.jar.JarFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SmallryeCommonIoTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void currentDirectoryAndParentResolutionNormalizeRelativePaths() {
        Path currentDirectory = Files2.currentDirectory();

        assertThat(currentDirectory).isAbsolute();
        assertThat(currentDirectory.normalize()).isEqualTo(currentDirectory);
        assertThat(Files2.getParent(Path.of("alpha", "..", "beta", "child.txt")))
                .isEqualTo(currentDirectory.resolve("beta"));
        assertThat(Files2.getParent(currentDirectory.resolve("nested").resolve("..").resolve("leaf.txt")))
                .isEqualTo(currentDirectory);
    }

    @Test
    void deleteStatsBehavesLikeARecordValueObject() {
        DeleteStats stats = new DeleteStats(2, 1, 4, 3);
        DeleteStats sameStats = new DeleteStats(2, 1, 4, 3);
        DeleteStats differentStats = new DeleteStats(2, 2, 4, 4);

        assertThat(stats.directoriesFound()).isEqualTo(2);
        assertThat(stats.directoriesRemoved()).isEqualTo(1);
        assertThat(stats.filesFound()).isEqualTo(4);
        assertThat(stats.filesRemoved()).isEqualTo(3);
        assertThat(stats).isEqualTo(sameStats).hasSameHashCodeAs(sameStats);
        assertThat(stats).isNotEqualTo(differentStats);
        assertThat(stats.toString())
                .contains("directoriesFound=2", "directoriesRemoved=1", "filesFound=4", "filesRemoved=3");
    }

    @Test
    void quietRecursiveDeleteReportsRemovedDirectoryTree() throws IOException {
        Path root = newWorkspace("quiet-delete-root");
        Path nested = root.resolve("one").resolve("two");
        Files.createDirectories(nested);
        Files.writeString(root.resolve("root.txt"), "root", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("one").resolve("middle.txt"), "middle", StandardCharsets.UTF_8);
        Files.writeString(nested.resolve("leaf.txt"), "leaf", StandardCharsets.UTF_8);

        DeleteStats stats = Files2.deleteRecursivelyQuietlyEvenIfInsecure(root);

        assertThat(stats).isEqualTo(new DeleteStats(3, 3, 3, 3));
        assertThat(root).doesNotExist();
        assertThat(Files2.deleteRecursivelyQuietlyEvenIfInsecure(root)).isEqualTo(new DeleteStats(0, 0, 0, 0));
    }

    @Test
    void recursiveDeleteRemovesLinksWithoutFollowingThem() throws IOException {
        Path root = newWorkspace("delete-root");
        Path nested = root.resolve("nested");
        Path outsideTarget = temporaryDirectory.resolve("outside-target");
        Files.createDirectories(nested);
        Files.createDirectories(outsideTarget);
        Files.writeString(nested.resolve("payload.txt"), "payload", StandardCharsets.UTF_8);
        Files.writeString(outsideTarget.resolve("survivor.txt"), "survivor", StandardCharsets.UTF_8);
        Optional<Path> symbolicLink = createSymbolicLinkIfSupported(root.resolve("target-link"), outsideTarget);

        Files2.deleteRecursivelyEvenIfInsecure(root);

        assertThat(root).doesNotExist();
        assertThat(outsideTarget.resolve("survivor.txt")).exists();
        assertThat(Files.readString(outsideTarget.resolve("survivor.txt"), StandardCharsets.UTF_8))
                .isEqualTo("survivor");
        symbolicLink.ifPresent(link -> assertThat(link).doesNotExist());
    }

    @Test
    void cleanRecursivelyKeepsDirectoryButRemovesChildren() throws IOException {
        Path root = newWorkspace("clean-root");
        Files.createDirectories(root.resolve("left"));
        Files.createDirectories(root.resolve("right").resolve("deep"));
        Files.writeString(root.resolve("left").resolve("left.txt"), "left", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("right").resolve("deep").resolve("right.txt"), "right", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("top.txt"), "top", StandardCharsets.UTF_8);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            Files2.cleanRecursivelyEvenIfInsecure(stream);
        }

        assertThat(root).isDirectory();
        assertThat(listDirectory(root)).containsExactlyInAnyOrder(root.resolve("left"), root.resolve("right"));
        assertThat(listDirectory(root.resolve("left"))).isEmpty();
        assertThat(listDirectory(root.resolve("right"))).containsExactly(root.resolve("right").resolve("deep"));
        assertThat(listDirectory(root.resolve("right").resolve("deep"))).isEmpty();

        Path singleFile = root.resolve("single-file.txt");
        Files.writeString(singleFile, "delete me", StandardCharsets.UTF_8);
        Files2.cleanRecursivelyEvenIfInsecure(singleFile);
        assertThat(singleFile).doesNotExist();
    }

    @Test
    void secureDirectoryOperationsEitherOpenStreamsOrReportUnsupportedFileSystems() throws IOException {
        Path root = newWorkspace("secure-root");
        Path child = root.resolve("child");
        Files.createDirectories(child.resolve("nested"));
        Files.writeString(child.resolve("nested").resolve("payload.txt"), "payload", StandardCharsets.UTF_8);

        if (Files2.hasSecureDirectories()) {
            try (SecureDirectoryStream<Path> secureDirectoryStream = Files2.newSecureDirectoryStream(root)) {
                Files2.deleteRecursively(secureDirectoryStream, child.getFileName());
            }
            assertThat(child).doesNotExist();
            assertThat(root).isDirectory();
            return;
        }

        assertThatThrownBy(() -> Files2.newSecureDirectoryStream(root))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Files2.deleteRecursively(root))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(child.resolve("nested").resolve("payload.txt")).exists();
    }

    @Test
    void jarFileFactoryOpensMultiReleaseJarsAtRuntimeVersion() throws IOException {
        Path jarPath = temporaryDirectory.resolve("multi-release.jar");
        createJar(jarPath, true);

        try (JarFile jarFile = JarFiles.create(jarPath.toFile())) {
            JarEntry entry = jarFile.getJarEntry("sample.txt");

            assertThat(JarFiles.isMultiRelease(jarFile)).isTrue();
            assertThat(entry.getName()).isEqualTo("sample.txt");
            assertThat(JarEntries.getRealName(entry)).isEqualTo("META-INF/versions/9/sample.txt");
            assertThat(readJarEntry(jarFile, entry)).isEqualTo("version-nine");
        }
    }

    @Test
    void jarFileFactorySupportsStringAndVerificationOverloadsForRegularJars() throws IOException {
        Path jarPath = temporaryDirectory.resolve("regular.jar");
        createJar(jarPath, false);

        try (JarFile fromString = JarFiles.create(jarPath.toString(), false);
                JarFile fromFileWithVerification = JarFiles.create(jarPath.toFile(), true)) {
            JarEntry stringEntry = fromString.getJarEntry("sample.txt");
            JarEntry fileEntry = fromFileWithVerification.getJarEntry("sample.txt");

            assertThat(JarFiles.isMultiRelease(fromString)).isFalse();
            assertThat(JarFiles.isMultiRelease(fromFileWithVerification)).isFalse();
            assertThat(JarEntries.getRealName(stringEntry)).isEqualTo("sample.txt");
            assertThat(readJarEntry(fromString, stringEntry)).isEqualTo("base");
            assertThat(readJarEntry(fromFileWithVerification, fileEntry)).isEqualTo("base");
        }
    }

    private static List<Path> listDirectory(Path directory) throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            stream.forEach(children::add);
        }
        return children;
    }

    private static Path newWorkspace(String name) throws IOException {
        Path workspace = Path.of("build", "smallrye-common-io-tests", name + "-" + System.nanoTime());
        Files.createDirectories(workspace);
        return workspace;
    }

    private static Optional<Path> createSymbolicLinkIfSupported(Path link, Path target) {
        try {
            return Optional.of(Files.createSymbolicLink(link, target));
        } catch (UnsupportedOperationException | IOException exception) {
            return Optional.empty();
        }
    }

    private static void createJar(Path jarPath, boolean multiRelease) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (multiRelease) {
            attributes.putValue("Multi-Release", "true");
        }

        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            writeJarEntry(outputStream, "sample.txt", "base");
            if (multiRelease) {
                writeJarEntry(outputStream, "META-INF/versions/9/sample.txt", "version-nine");
            }
        }
    }

    private static void writeJarEntry(JarOutputStream outputStream, String name, String content) throws IOException {
        JarEntry entry = new JarEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        outputStream.closeEntry();
    }

    private static String readJarEntry(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
