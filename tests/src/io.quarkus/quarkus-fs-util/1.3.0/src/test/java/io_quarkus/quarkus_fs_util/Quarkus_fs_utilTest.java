/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_fs_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quarkus.fs.util.FileSystemHelper;
import io.quarkus.fs.util.FileSystemProviders;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.fs.util.sysfs.ConfigurableFileSystemProviderWrapper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Quarkus_fs_utilTest {
    @TempDir
    Path tempDir;

    @Test
    void zipAndUnzipDirectoryPreservesNestedContents() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("nested/deeper"));
        Files.writeString(source.resolve("alpha.txt"), "alpha", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("nested/beta.txt"), "beta", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("nested/deeper/gamma.txt"), "gamma", StandardCharsets.UTF_8);

        Path zip = tempDir.resolve("archive.zip");
        ZipUtils.zip(source, zip);

        Path target = tempDir.resolve("target");
        ZipUtils.unzip(zip, target);

        assertThat(Files.readString(target.resolve("alpha.txt"), StandardCharsets.UTF_8)).isEqualTo("alpha");
        assertThat(Files.readString(target.resolve("nested/beta.txt"), StandardCharsets.UTF_8)).isEqualTo("beta");
        assertThat(Files.readString(target.resolve("nested/deeper/gamma.txt"), StandardCharsets.UTF_8))
                .isEqualTo("gamma");
    }

    @Test
    void zipSingleFileStoresItUnderOriginalFileName() throws Exception {
        Path source = tempDir.resolve("note.txt");
        Files.writeString(source, "single file payload", StandardCharsets.UTF_8);

        Path zip = tempDir.resolve("single-file.zip");
        ZipUtils.zip(source, zip);

        try (FileSystem zipFileSystem = ZipUtils.newFileSystem(zip)) {
            Path entry = zipFileSystem.getPath("note.txt");

            assertThat(Files.isRegularFile(entry)).isTrue();
            assertThat(Files.readString(entry, StandardCharsets.UTF_8)).isEqualTo("single file payload");
        }
    }

    @Test
    void newZipCreatesMissingParentDirectoriesAndCanBeReadBack() throws Exception {
        Path zip = tempDir.resolve("missing/parents/created.zip");

        try (FileSystem zipFileSystem = ZipUtils.newZip(zip)) {
            Files.createDirectories(zipFileSystem.getPath("config"));
            Files.writeString(
                    zipFileSystem.getPath("config/application.properties"), "key=value", StandardCharsets.UTF_8);
        }

        assertThat(Files.isDirectory(zip.getParent())).isTrue();
        try (FileSystem zipFileSystem = ZipUtils.newFileSystem(zip)) {
            assertThat(Files.readString(zipFileSystem.getPath("config/application.properties"), StandardCharsets.UTF_8))
                    .isEqualTo("key=value");
        }
    }

    @Test
    void configurableFileSystemProviderAllowsOnlyConfiguredAccessModes() {
        Path missing = tempDir.resolve("missing-executable");
        ConfigurableFileSystemProviderWrapper provider = new ConfigurableFileSystemProviderWrapper(
                missing.getFileSystem().provider(), Set.of(AccessMode.EXECUTE));

        assertThatCode(() -> provider.checkAccess(missing, AccessMode.EXECUTE)).doesNotThrowAnyException();
        assertThatExceptionOfType(NoSuchFileException.class)
                .isThrownBy(() -> provider.checkAccess(missing, AccessMode.READ));
    }

    @Test
    void copyFromZipCopiesOnlySelectedSubtree() throws Exception {
        Path zip = tempDir.resolve("subtree.zip");
        try (FileSystem zipFileSystem = ZipUtils.newZip(zip)) {
            Files.createDirectories(zipFileSystem.getPath("root/child"));
            Files.writeString(zipFileSystem.getPath("root/child/data.txt"), "copied", StandardCharsets.UTF_8);
            Files.createDirectories(zipFileSystem.getPath("other"));
            Files.writeString(zipFileSystem.getPath("other/ignored.txt"), "ignored", StandardCharsets.UTF_8);
        }

        Path target = tempDir.resolve("subtree-target");
        Files.createDirectories(target);
        try (FileSystem zipFileSystem = ZipUtils.newFileSystem(zip)) {
            ZipUtils.copyFromZip(zipFileSystem.getPath("root"), target);
        }

        assertThat(Files.readString(target.resolve("child/data.txt"), StandardCharsets.UTF_8)).isEqualTo("copied");
        assertThat(target.resolve("ignored.txt")).doesNotExist();
    }

    @Test
    void zipReproduciblyCreatesStableArchiveBytesAndReadableEntries() throws Exception {
        Path sourceA = tempDir.resolve("source-a");
        Path sourceB = tempDir.resolve("source-b");
        createSameTreeInNaturalOrder(sourceA);
        createSameTreeInReverseOrder(sourceB);

        Instant entryTime = Instant.parse("2020-01-02T03:04:05Z");
        Path zipA = tempDir.resolve("reproducible-a.zip");
        Path zipB = tempDir.resolve("reproducible-b.zip");

        ZipUtils.zipReproducibly(sourceA, zipA, entryTime);
        ZipUtils.zipReproducibly(sourceB, zipB, entryTime);

        assertThat(Files.readAllBytes(zipA)).isEqualTo(Files.readAllBytes(zipB));
        assertThat(zipEntryNames(zipA)).contains("alpha.txt", "nested/beta.txt", "nested/gamma.txt");

        Path unpacked = tempDir.resolve("reproducible-unpacked");
        ZipUtils.unzip(zipA, unpacked);
        assertThat(Files.readString(unpacked.resolve("alpha.txt"), StandardCharsets.UTF_8)).isEqualTo("alpha");
        assertThat(Files.readString(unpacked.resolve("nested/beta.txt"), StandardCharsets.UTF_8)).isEqualTo("beta");
        assertThat(Files.readString(unpacked.resolve("nested/gamma.txt"), StandardCharsets.UTF_8)).isEqualTo("gamma");
    }

    @Test
    void createNewReproducibleZipFileSystemRejectsExistingTargetAndWritesEntries() throws Exception {
        Path existing = tempDir.resolve("existing.zip");
        Files.writeString(existing, "already here", StandardCharsets.UTF_8);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ZipUtils.createNewReproducibleZipFileSystem(existing, Instant.EPOCH))
                .withMessageContaining(existing.toString());

        Path zip = tempDir.resolve("new-reproducible.zip");
        try (FileSystem zipFileSystem = ZipUtils.createNewReproducibleZipFileSystem(zip, Instant.EPOCH)) {
            Files.createDirectories(zipFileSystem.getPath("META-INF"));
            Files.writeString(zipFileSystem.getPath("META-INF/example.txt"), "created", StandardCharsets.UTF_8);
        }

        try (FileSystem zipFileSystem = ZipUtils.newFileSystem(zip)) {
            assertThat(Files.readString(zipFileSystem.getPath("META-INF/example.txt"), StandardCharsets.UTF_8))
                    .isEqualTo("created");
        }
    }

    @Test
    void reproducibleZipFileSystemNormalizesEntryTimestampsAndUnixPermissions() throws Exception {
        Instant entryTime = Instant.parse("2021-04-05T06:07:08Z");
        Path zip = tempDir.resolve("normalized-attributes.zip");

        try (FileSystem zipFileSystem = ZipUtils.createNewReproducibleZipFileSystem(zip, entryTime)) {
            Files.createDirectories(zipFileSystem.getPath("assets"));
            Files.writeString(zipFileSystem.getPath("assets/data.txt"), "normalized", StandardCharsets.UTF_8);
        }

        FileTime expectedTime = FileTime.fromMillis(entryTime.toEpochMilli());
        Set<PosixFilePermission> expectedDirectoryPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
        Set<PosixFilePermission> expectedFilePermissions = PosixFilePermissions.fromString("rw-r--r--");
        try (FileSystem zipFileSystem = ZipUtils.newFileSystem(
                ZipUtils.toZipUri(zip), Map.of("enablePosixFileAttributes", "true"))) {
            Path directory = zipFileSystem.getPath("assets");
            Path file = zipFileSystem.getPath("assets/data.txt");

            assertThat(Files.getLastModifiedTime(directory)).isEqualTo(expectedTime);
            assertThat(Files.getLastModifiedTime(file)).isEqualTo(expectedTime);
            assertThat(Files.getPosixFilePermissions(directory)).isEqualTo(expectedDirectoryPermissions);
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(expectedFilePermissions);
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("normalized");
        }
    }

    @Test
    void fileSystemHelperOpensZipFilesAndEscapesBangCharactersInUris() throws Exception {
        Path directoryWithBang = tempDir.resolve("bang!");
        Path zip = directoryWithBang.resolve("archive.zip");
        try (FileSystem zipFileSystem = ZipUtils.newZip(zip)) {
            Files.writeString(zipFileSystem.getPath("entry.txt"), "from zip", StandardCharsets.UTF_8);
        }

        URI zipUri = ZipUtils.toZipUri(zip);

        assertThat(zipUri.getScheme()).isEqualTo("jar");
        assertThat(zipUri.toASCIIString()).contains("bang%21/archive.zip!/");
        try (FileSystem zipFileSystem = FileSystemHelper.openFS(zip, Map.of(), getClass().getClassLoader())) {
            assertThat(Files.readString(zipFileSystem.getPath("entry.txt"), StandardCharsets.UTF_8))
                    .isEqualTo("from zip");
        }
    }

    @Test
    void ignoreFileWriteabilityKeepsPathOperationsUsableWhileAllowingWriteAccessChecks() throws Exception {
        Path file = tempDir.resolve("readonly-check.txt");
        Files.writeString(file, "initial", StandardCharsets.UTF_8);

        Path wrapped = FileSystemHelper.ignoreFileWriteability(file);

        assertThat(wrapped.getFileSystem()).isNotSameAs(file.getFileSystem());
        assertThat(wrapped.getFileSystem().provider()).isNotSameAs(file.getFileSystem().provider());
        assertThat(Files.exists(wrapped)).isTrue();
        assertThat(Files.readString(wrapped, StandardCharsets.UTF_8)).isEqualTo("initial");
        assertThatCode(() -> wrapped.getFileSystem().provider().checkAccess(wrapped, AccessMode.WRITE))
                .doesNotThrowAnyException();
    }

    @Test
    void exposesUsableJarFileSystemProviderAndOutputStreamWrapperIsTransparent() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStream wrapped = ZipUtils.wrapForJDK8232879(outputStream);

        wrapped.write("payload".getBytes(StandardCharsets.UTF_8));
        wrapped.flush();

        assertThat(FileSystemProviders.ZIP_PROVIDER.getScheme()).isEqualTo("jar");
        assertThat(wrapped).isSameAs(outputStream);
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("payload");
    }

    private static void createSameTreeInNaturalOrder(Path root) throws Exception {
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("alpha.txt"), "alpha", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nested/beta.txt"), "beta", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nested/gamma.txt"), "gamma", StandardCharsets.UTF_8);
    }

    private static void createSameTreeInReverseOrder(Path root) throws Exception {
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/gamma.txt"), "gamma", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nested/beta.txt"), "beta", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("alpha.txt"), "alpha", StandardCharsets.UTF_8);
    }

    private static List<String> zipEntryNames(Path zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        Collections.sort(names);
        return names;
    }
}
