/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.common.io.DeleteStats;
import io.smallrye.common.io.Files2;
import io.smallrye.common.io.jar.JarEntries;
import io.smallrye.common.io.jar.JarFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Smallrye_common_ioTest {
    @Test
    void deleteStatsExposesRecordValuesAndValidatesBounds() {
        DeleteStats stats = new DeleteStats(2, 1, 3, 2);
        DeleteStats sameStats = new DeleteStats(2, 1, 3, 2);

        assertThat(stats.directoriesFound()).isEqualTo(2);
        assertThat(stats.directoriesRemoved()).isEqualTo(1);
        assertThat(stats.filesFound()).isEqualTo(3);
        assertThat(stats.filesRemoved()).isEqualTo(2);
        assertThat(stats).isEqualTo(sameStats).hasSameHashCodeAs(sameStats);
        assertThat(stats.toString())
                .contains("directoriesFound=2", "directoriesRemoved=1", "filesFound=3", "filesRemoved=2");

        assertThatThrownBy(() -> new DeleteStats(-1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeleteStats(1, 2, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeleteStats(0, 0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeleteStats(0, 0, 1, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathHelpersExposeStableCurrentDirectoryAndParentResolution() {
        Path expectedCurrentDirectory = Path.of(System.getProperty("user.dir", ".")).normalize().toAbsolutePath();

        assertThat(Files2.currentDirectory()).isEqualTo(expectedCurrentDirectory);
        assertThat(Files2.getParent(Path.of("child.txt"))).isEqualTo(expectedCurrentDirectory);
        assertThat(Files2.getParent(Path.of("nested", "child.txt")))
                .isEqualTo(expectedCurrentDirectory.resolve("nested"));
        assertThat(Files2.getParent(expectedCurrentDirectory.resolve("absolute-child.txt")))
                .isEqualTo(expectedCurrentDirectory);
        assertThatThrownBy(() -> Files2.getParent(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void insecureDeletionUtilitiesDeleteTreesAndReportQuietStats(@TempDir Path tempDir) throws IOException {
        Path deleteTarget = createTree(tempDir.resolve("delete-target"));
        Files2.deleteRecursivelyEvenIfInsecure(deleteTarget);
        assertThat(deleteTarget).doesNotExist();

        Path quietTarget = createTree(tempDir.resolve("quiet-target"));
        DeleteStats stats = Files2.deleteRecursivelyQuietlyEvenIfInsecure(quietTarget);
        assertThat(stats.directoriesFound()).isEqualTo(2);
        assertThat(stats.directoriesRemoved()).isEqualTo(2);
        assertThat(stats.filesFound()).isEqualTo(2);
        assertThat(stats.filesRemoved()).isEqualTo(2);
        assertThat(quietTarget).doesNotExist();

        DeleteStats missingStats = Files2.deleteRecursivelyQuietlyEvenIfInsecure(tempDir.resolve("missing"));
        assertThat(missingStats).isEqualTo(new DeleteStats(0, 0, 0, 0));
    }

    @Test
    void insecureDirectoryStreamOverloadsCleanAndDeleteContents(@TempDir Path tempDir) throws IOException {
        Path cleanRoot = createTree(tempDir.resolve("clean-root"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cleanRoot)) {
            Files2.cleanRecursivelyEvenIfInsecure(stream);
        }
        assertThat(cleanRoot).isDirectory();
        assertThat(cleanRoot.resolve("nested")).isDirectory();
        assertThat(cleanRoot.resolve("root.txt")).doesNotExist();
        assertThat(cleanRoot.resolve("nested/child.txt")).doesNotExist();

        Path deleteRoot = createTree(tempDir.resolve("delete-root"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(deleteRoot)) {
            DeleteStats stats = Files2.deleteRecursivelyQuietlyEvenIfInsecure(stream);
            assertThat(stats.directoriesFound()).isEqualTo(1);
            assertThat(stats.directoriesRemoved()).isEqualTo(1);
            assertThat(stats.filesFound()).isEqualTo(2);
            assertThat(stats.filesRemoved()).isEqualTo(2);
        }
        assertThat(deleteRoot).isDirectory();
        assertThat(fileNames(deleteRoot)).isEmpty();

        Path throwingDeleteRoot = createTree(tempDir.resolve("throwing-delete-root"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(throwingDeleteRoot)) {
            Files2.deleteRecursivelyEvenIfInsecure(stream);
        }
        assertThat(throwingDeleteRoot).isDirectory();
        assertThat(fileNames(throwingDeleteRoot)).isEmpty();
    }

    @Test
    void cleanRecursivelyPreservesDirectoriesAndDeletesFiles(@TempDir Path tempDir) throws IOException {
        Path root = createTree(tempDir.resolve("root"));

        Files2.cleanRecursivelyEvenIfInsecure(root);

        assertThat(root).isDirectory();
        assertThat(root.resolve("nested")).isDirectory();
        assertThat(root.resolve("root.txt")).doesNotExist();
        assertThat(root.resolve("nested/child.txt")).doesNotExist();
        assertThat(fileNames(root)).containsExactly("nested");
        assertThat(fileNames(root.resolve("nested"))).isEmpty();
    }

    @Test
    void cleanRecursivelyOnFileTargetsRemovesOnlyTheFile(@TempDir Path tempDir) throws IOException {
        Path insecureFile = tempDir.resolve("insecure-file.txt");
        Path insecureSibling = tempDir.resolve("insecure-sibling.txt");
        Files.writeString(insecureFile, "remove me");
        Files.writeString(insecureSibling, "keep me");

        Files2.cleanRecursivelyEvenIfInsecure(insecureFile);

        assertThat(insecureFile).doesNotExist();
        assertThat(insecureSibling).hasContent("keep me");

        if (Files2.hasSecureDirectories()) {
            Path secureFile = tempDir.resolve("secure-file.txt");
            Path secureSibling = tempDir.resolve("secure-sibling.txt");
            Files.writeString(secureFile, "remove me securely");
            Files.writeString(secureSibling, "keep me securely");

            Files2.cleanRecursively(secureFile);

            assertThat(secureFile).doesNotExist();
            assertThat(secureSibling).hasContent("keep me securely");
        }
    }

    @Test
    void recursiveDeletionRemovesDirectorySymbolicLinksWithoutFollowingThem(@TempDir Path tempDir) throws IOException {
        Path externalTarget = tempDir.resolve("external-target");
        Files.createDirectories(externalTarget);
        Path externalFile = externalTarget.resolve("preserved.txt");
        Files.writeString(externalFile, "preserved");

        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ordinary.txt"), "ordinary");
        Path symbolicLink = root.resolve("linked-target");
        Files.createSymbolicLink(symbolicLink, externalTarget);

        DeleteStats stats = Files2.deleteRecursivelyQuietlyEvenIfInsecure(root);

        assertThat(root).doesNotExist();
        assertThat(Files.exists(symbolicLink, LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(externalTarget).isDirectory();
        assertThat(externalFile).hasContent("preserved");
        assertThat(stats.directoriesFound()).isEqualTo(1);
        assertThat(stats.directoriesRemoved()).isEqualTo(1);
        assertThat(stats.filesFound()).isEqualTo(2);
        assertThat(stats.filesRemoved()).isEqualTo(2);
    }

    @Test
    void secureDirectoryUtilitiesOperateOnPathAndStreamOverloads(@TempDir Path tempDir) throws IOException {
        Path secureRoot = tempDir.resolve("secure-root");
        Files.createDirectories(secureRoot);

        if (Files2.hasSecureDirectories()) {
            Path pathCleanTarget = createTree(secureRoot.resolve("path-clean"));
            Files2.cleanRecursively(pathCleanTarget);
            assertThat(pathCleanTarget).isDirectory();
            assertThat(pathCleanTarget.resolve("nested")).isDirectory();
            assertThat(pathCleanTarget.resolve("root.txt")).doesNotExist();
            assertThat(pathCleanTarget.resolve("nested/child.txt")).doesNotExist();

            Path pathDeleteTarget = createTree(secureRoot.resolve("path-delete"));
            Files2.deleteRecursively(pathDeleteTarget);
            assertThat(pathDeleteTarget).doesNotExist();

            Path quietPathDeleteTarget = createTree(secureRoot.resolve("quiet-path-delete"));
            DeleteStats quietPathStats = Files2.deleteRecursivelyQuietly(quietPathDeleteTarget);
            assertTreeDeletedWithExpectedStats(quietPathDeleteTarget, quietPathStats, 2, 2);

            createTree(secureRoot.resolve("stream-clean"));
            createTree(secureRoot.resolve("stream-delete"));
            createTree(secureRoot.resolve("stream-quiet-delete"));
            try (SecureDirectoryStream<Path> stream = Files2.newSecureDirectoryStream(
                    secureRoot, LinkOption.NOFOLLOW_LINKS)) {
                Files2.cleanRecursively(stream, Path.of("stream-clean"));
                assertThat(secureRoot.resolve("stream-clean")).isDirectory();
                assertThat(secureRoot.resolve("stream-clean/nested")).isDirectory();
                assertThat(secureRoot.resolve("stream-clean/root.txt")).doesNotExist();
                assertThat(secureRoot.resolve("stream-clean/nested/child.txt")).doesNotExist();

                Files2.deleteRecursively(stream, Path.of("stream-delete"));
                assertThat(secureRoot.resolve("stream-delete")).doesNotExist();

                DeleteStats quietStreamStats = Files2.deleteRecursivelyQuietly(stream, Path.of("stream-quiet-delete"));
                assertTreeDeletedWithExpectedStats(secureRoot.resolve("stream-quiet-delete"), quietStreamStats, 2, 2);
            }

            Path cleanAll = createTree(secureRoot.resolve("clean-all"));
            try (SecureDirectoryStream<Path> stream = Files2.newSecureDirectoryStream(cleanAll)) {
                Files2.cleanRecursively(stream);
            }
            assertThat(cleanAll).isDirectory();
            assertThat(cleanAll.resolve("nested")).isDirectory();
            assertThat(cleanAll.resolve("root.txt")).doesNotExist();
            assertThat(cleanAll.resolve("nested/child.txt")).doesNotExist();

            Path quietAll = createTree(secureRoot.resolve("quiet-all"));
            try (SecureDirectoryStream<Path> stream = Files2.newSecureDirectoryStream(quietAll)) {
                DeleteStats quietAllStats = Files2.deleteRecursivelyQuietly(stream);
                assertThat(quietAllStats.directoriesFound()).isEqualTo(1);
                assertThat(quietAllStats.directoriesRemoved()).isEqualTo(1);
                assertThat(quietAllStats.filesFound()).isEqualTo(2);
                assertThat(quietAllStats.filesRemoved()).isEqualTo(2);
            }
            assertThat(quietAll).isDirectory();
            assertThat(fileNames(quietAll)).isEmpty();

            Path deleteAll = createTree(secureRoot.resolve("delete-all"));
            try (SecureDirectoryStream<Path> stream = Files2.newSecureDirectoryStream(deleteAll)) {
                Files2.deleteRecursively(stream);
            }
            assertThat(deleteAll).isDirectory();
            assertThat(fileNames(deleteAll)).isEmpty();
        } else {
            assertThatThrownBy(() -> Files2.newSecureDirectoryStream(secureRoot))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> Files2.cleanRecursively(secureRoot))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> Files2.deleteRecursively(secureRoot))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> Files2.deleteRecursivelyQuietly(secureRoot))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void jarUtilitiesCreateRuntimeAwareJarFilesAndResolveRealEntryNames(@TempDir Path tempDir) throws IOException {
        Path multiReleaseJar = tempDir.resolve("multi-release.jar");
        writeJar(multiReleaseJar, true);

        try (JarFile jarFile = JarFiles.create(multiReleaseJar.toFile())) {
            assertThat(JarFiles.isMultiRelease(jarFile)).isTrue();
            JarEntry entry = jarFile.getJarEntry("sample.txt");
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("sample.txt");
            assertThat(JarEntries.getRealName(entry)).isEqualTo("META-INF/versions/9/sample.txt");
        }

        try (JarFile jarFile = JarFiles.create(multiReleaseJar.toString(), false)) {
            assertThat(JarFiles.isMultiRelease(jarFile)).isTrue();
            assertThat(JarEntries.getRealName(jarFile.getJarEntry("sample.txt")))
                    .isEqualTo("META-INF/versions/9/sample.txt");
        }
    }

    @Test
    void jarUtilitiesHandleRegularJarsAndVerifyOverloads(@TempDir Path tempDir) throws IOException {
        Path regularJar = tempDir.resolve("regular.jar");
        writeJar(regularJar, false);

        try (JarFile jarFile = JarFiles.create(regularJar.toFile(), false)) {
            assertThat(JarFiles.isMultiRelease(jarFile)).isFalse();
            JarEntry entry = jarFile.getJarEntry("sample.txt");
            assertThat(entry).isNotNull();
            assertThat(JarEntries.getRealName(entry)).isEqualTo("sample.txt");
        }

        try (JarFile jarFile = JarFiles.create(regularJar.toString())) {
            assertThat(JarFiles.isMultiRelease(jarFile)).isFalse();
            assertThat(JarEntries.getRealName(jarFile.getJarEntry("sample.txt"))).isEqualTo("sample.txt");
        }
    }

    private static Path createTree(Path root) throws IOException {
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("root.txt"), "root");
        Files.writeString(root.resolve("nested/child.txt"), "child");
        return root;
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static void assertTreeDeletedWithExpectedStats(
            Path root, DeleteStats stats, long expectedDirectories, long expectedFiles) {
        assertThat(root).doesNotExist();
        assertThat(stats.directoriesFound()).isEqualTo(expectedDirectories);
        assertThat(stats.directoriesRemoved()).isEqualTo(expectedDirectories);
        assertThat(stats.filesFound()).isEqualTo(expectedFiles);
        assertThat(stats.filesRemoved()).isEqualTo(expectedFiles);
    }

    private static void writeJar(Path jarPath, boolean multiRelease) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (multiRelease) {
            attributes.putValue("Multi-Release", "true");
        }

        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            writeJarEntry(jarOutputStream, "sample.txt", "base");
            if (multiRelease) {
                writeJarEntry(jarOutputStream, "META-INF/versions/9/sample.txt", "version-nine");
            }
        }
    }

    private static void writeJarEntry(JarOutputStream jarOutputStream, String name, String content) throws IOException {
        JarEntry entry = new JarEntry(name);
        jarOutputStream.putNextEntry(entry);
        jarOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        jarOutputStream.closeEntry();
    }
}
