/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

// Exercise TAR entry models, stream overloads, and file-backed reading.
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveSparseEntry;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.tar.TarArchiveStructSparse;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarCoverageTest {

    @Test
    void entriesRoundTripMetadataAndSparseState() throws Exception {
        final Path source = Files.createTempFile("tar-model", ".txt");
        Files.writeString(source, "payload", StandardCharsets.UTF_8);
        final TarArchiveEntry fileEntry = new TarArchiveEntry(source.toFile(), "dir/file.txt");
        final TarArchiveEntry pathEntry = new TarArchiveEntry(source, "path.txt", LinkOption.NOFOLLOW_LINKS);
        final TarArchiveEntry link = new TarArchiveEntry("link", TarArchiveEntry.LF_SYMLINK);
        final TarArchiveEntry directory = new TarArchiveEntry("dir/", true);
        final TarArchiveEntry named = new TarArchiveEntry("named", TarArchiveEntry.LF_NORMAL, true);
        final FileTime time = FileTime.fromMillis(123_000);
        named.setNames("changed", "user");
        named.setName("changed");
        named.setCreationTime(time);
        named.setLastAccessTime(time);
        named.setLastModifiedTime(time);
        named.setStatusChangeTime(time);
        named.setModTime(time);
        named.setModTime(new Date(124_000));
        named.setModTime(125_000);
        named.setGroupId(7);
        named.setGroupId(8L);
        named.setUserId(9);
        named.setUserId(10L);
        named.setGroupName("group");
        named.setUserName("user");
        named.setDevMajor(11);
        named.setDevMinor(12);
        named.setMode(0640);
        named.setLinkName("target");
        named.setSize(13);
        named.setDataOffset(14);
        named.setSparseHeaders(List.of(new TarArchiveStructSparse(2, 3)));
        named.addPaxHeader("comment", "value");
        assertThat(named.getName()).isEqualTo("changed");
        assertThat(named.getGroupId()).isEqualTo(8);
        assertThat(named.getLongGroupId()).isEqualTo(8);
        assertThat(named.getUserId()).isEqualTo(10);
        assertThat(named.getLongUserId()).isEqualTo(10);
        assertThat(named.getGroupName()).isEqualTo("group");
        assertThat(named.getUserName()).isEqualTo("user");
        assertThat(named.getLinkName()).isEqualTo("target");
        assertThat(named.getRealSize()).isEqualTo(13);
        assertThat(named.getDataOffset()).isEqualTo(14);
        assertThat(named.getCreationTime()).isEqualTo(time);
        assertThat(named.getLastAccessTime()).isEqualTo(time);
        assertThat(named.getStatusChangeTime()).isEqualTo(time);
        assertThat(named.getExtraPaxHeader("comment")).isEqualTo("value");
        assertThat(named.getSparseHeaders()).containsExactly(new TarArchiveStructSparse(2, 3));
        assertThat(named.getOrderedSparseHeaders()).containsExactly(new TarArchiveStructSparse(2, 3));
        named.clearExtraPaxHeaders();
        assertThat(named.getExtraPaxHeaders()).isEmpty();
        assertThat(directory.isDirectory()).isTrue();
        assertThat(fileEntry.isFile()).isTrue();
        assertThat(fileEntry.getFile()).isEqualTo(source.toFile());
        assertThat(pathEntry.getPath()).isEqualTo(source);
        assertThat(link.isSymbolicLink()).isTrue();
        assertThat(new TarArchiveEntry("dir/").isDescendent(fileEntry)).isTrue();
        assertThat(fileEntry).isEqualTo(fileEntry);
        assertThat(fileEntry.hashCode()).isEqualTo(fileEntry.hashCode());
        final byte[] header = new byte[512];
        named.writeEntryHeader(header);
        final TarArchiveEntry parsed = new TarArchiveEntry(header);
        final org.apache.commons.compress.archivers.zip.ZipEncoding encoding =
                org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("UTF-8");
        assertThat(new TarArchiveEntry(header, encoding).getName()).isEqualTo("changed");
        assertThat(new TarArchiveEntry(header, encoding, true).getName()).isEqualTo("changed");
        assertThat(new TarArchiveEntry(header, encoding, true, 0).getName()).isEqualTo("changed");
        assertThat(parsed.getName()).isEqualTo("changed");
        assertThat(parsed.isCheckSumOK()).isTrue();
        final byte[] customHeader = new byte[512];
        named.writeEntryHeader(customHeader, org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("UTF-8"), true);
        final org.apache.commons.compress.archivers.zip.ZipEncoding utf8 =
                org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("UTF-8");
        assertThat(utf8.encode("name-\uD83D\uDE00").remaining()).isGreaterThan(0);
        final TarArchiveEntry unicode = new TarArchiveEntry("name-\uD83D\uDE00");
        unicode.writeEntryHeader(new byte[512], utf8, true);
        assertThat(new TarArchiveSparseEntry(new byte[512]).getSparseHeaders()).isNotNull();
        assertThat(new TarArchiveSparseEntry(new byte[512]).isExtended()).isFalse();
        assertThat(TarUtils.parseBoolean(new byte[] {1}, 0)).isTrue();
        final byte[] octal = new byte[8];
        TarUtils.formatOctalBytes(42, octal, 0, octal.length);
        assertThat(TarUtils.parseOctal(octal, 0, octal.length)).isEqualTo(42);
        assertThatThrownBy(() -> TarUtils.parseOctal(new byte[] {'8', ' ', 0}, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        final byte[] binary = new byte[12];
        TarUtils.formatLongOctalOrBinaryBytes(Long.MAX_VALUE, binary, 0, binary.length);
        assertThat(TarUtils.parseOctalOrBinary(binary, 0, binary.length)).isEqualTo(Long.MAX_VALUE);
        final TarArchiveStructSparse sparse = TarUtils.parseSparse(new byte[24], 0);
        assertThat(sparse.getOffset()).isZero();
        assertThat(sparse.getNumbytes()).isZero();
        assertThat(sparse).hasToString("TarArchiveStructSparse{offset=0, numbytes=0}");
        Files.deleteIfExists(source);
    }

    @Test
    void sparseTarEntriesDriveStreamingAndFileBackedSkipPaths() throws Exception {
        final Path oldGnu = fixture("oldgnu_sparse.tar");
        try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(oldGnu))) {
            final TarArchiveEntry entry = input.getNextTarEntry();
            assertThat(entry.isOldGNUSparse()).isTrue();
            assertThat(input.skip(2048)).isGreaterThan(0);
            assertThat(input.readAllBytes()).isNotEmpty();
        }
        final Path paxGnu = fixture("pax_gnu_sparse.tar");
        try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(paxGnu))) {
            final TarArchiveEntry entry = input.getNextTarEntry();
            assertThat(entry.isPaxGNUSparse()).isTrue();
            assertThat(input.skip(1)).isGreaterThanOrEqualTo(0);
            assertThat(input.readAllBytes()).isNotEmpty();
        }
        for (final String name : new String[] {"posix00_sparse.tar", "posix01_sparse.tar", "posix10_sparse.tar"}) {
            try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(fixture(name)))) {
                assertThat(input.getNextTarEntry()).isNotNull();
                assertThat(input.readAllBytes()).isNotEmpty();
            }
        }
        try (TarFile tarFile = new TarFile(oldGnu)) {
            final TarArchiveEntry entry = tarFile.getEntries().get(0);
            try (InputStream input = tarFile.getInputStream(entry)) {
                assertThat(input.readAllBytes()).isNotEmpty();
            }
        }
    }

    @Test
    void longNamesGlobalPaxHeadersAndTarFileIndexingUsePublicEntries() throws Exception {
        for (final String name : new String[] {"archive_with_trailer.tar", "preepoch-posix.tar"}) {
            final Path archive = fixture(name);
            try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(archive))) {
                assertThat(input.getNextTarEntry()).isNotNull();
                input.getNextTarEntry();
            }
            try (TarFile input = new TarFile(archive)) {
                assertThat(input.getEntries()).isNotEmpty();
                final TarArchiveEntry entry = input.getEntries().get(0);
                try (InputStream content = input.getInputStream(entry)) {
                    content.readAllBytes();
                }
            }
        }
    }

    @Test
    void longNameAndGlobalPaxRecordsReachBothTarReaders() throws Exception {
        for (final String name : new String[] {
                "longpath/cygwin_gnu.tar", "longpath/cygwin_pax.tar", "longpath/minotaur_pax.tar"}) {
            final Path archive = fixture(name);
            try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(archive))) {
                TarArchiveEntry entry;
                while ((entry = input.getNextTarEntry()) != null) {
                    input.readAllBytes();
                    assertThat(entry.getName()).isNotEmpty();
                }
            }
            try (TarFile input = new TarFile(archive)) {
                for (final TarArchiveEntry entry : input.getEntries()) {
                    try (InputStream content = input.getInputStream(entry)) {
                        content.readAllBytes();
                    }
                }
            }
        }
    }

    @Test
    void starPaxTarVariantsDriveSparseMetadataThroughBothReaders() throws Exception {
        for (final String name : new String[] {"COMPRESS-612/test-times-xstar.tar",
                "COMPRESS-612/test-times-xstar-folder.tar", "COMPRESS-612/test-times-gnu-incremental.tar"}) {
            final Path archive = fixture(name);
            try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(archive))) {
                TarArchiveEntry entry;
                while ((entry = input.getNextTarEntry()) != null) {
                    input.readAllBytes();
                }
            }
            try (TarFile input = new TarFile(archive)) {
                for (final TarArchiveEntry entry : input.getEntries()) {
                    try (InputStream content = input.getInputStream(entry)) {
                        content.readAllBytes();
                    }
                }
            }
        }
    }

    @Test
    void globalPaxRecordIsConsumedByBothPublicTarReaders() throws Exception {
        final String globalPath = "global-name.txt";
        final byte[] paxBytes = paxRecord("path", globalPath).getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final org.apache.commons.compress.archivers.zip.ZipEncoding utf8 =
                org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("UTF-8");
        final TarArchiveEntry global = new TarArchiveEntry("global", TarConstants.LF_PAX_GLOBAL_EXTENDED_HEADER);
        global.setSize(paxBytes.length);
        final byte[] globalHeader = new byte[512];
        global.writeEntryHeader(globalHeader, utf8, false);
        bytes.write(globalHeader);
        bytes.write(paxBytes);
        bytes.write(new byte[512 - paxBytes.length]);
        final TarArchiveEntry file = new TarArchiveEntry("original.txt");
        file.setSize(5);
        final byte[] fileHeader = new byte[512];
        file.writeEntryHeader(fileHeader, utf8, false);
        bytes.write(fileHeader);
        bytes.write("hello".getBytes(StandardCharsets.UTF_8));
        bytes.write(new byte[507]);
        bytes.write(new byte[1024]);
        final byte[] archive = bytes.toByteArray();
        try (TarArchiveInputStream input = new TarArchiveInputStream(new ByteArrayInputStream(archive))) {
            final TarArchiveEntry entry = input.getNextTarEntry();
            assertThat(entry.getName()).isEqualTo(globalPath);
            assertThat(input.readAllBytes()).containsExactly("hello".getBytes(StandardCharsets.UTF_8));
        }
        final Path archiveFile = Files.createTempFile("global-pax", ".tar");
        try {
            Files.write(archiveFile, archive);
            try (TarFile input = new TarFile(archiveFile)) {
                assertThat(input.getEntries()).hasSize(1);
                assertThat(input.getEntries().get(0).getName()).isEqualTo(globalPath);
            }
        } finally {
            Files.deleteIfExists(archiveFile);
        }
    }

    @Test
    void binaryTarNumbersAndNegativeNamesUsePublicHeaderApis() throws Exception {
        for (final long value : new long[] {-123, -1, Long.MAX_VALUE}) {
            final byte[] encoded = new byte[12];
            TarUtils.formatLongOctalOrBinaryBytes(value, encoded, 0, encoded.length);
            assertThat(TarUtils.parseOctalOrBinary(encoded, 0, encoded.length)).isEqualTo(value);
        }
        // Eight-byte fields use the public binary-long path rather than the BigInteger path.
        final byte[] shortBinary = new byte[8];
        TarUtils.formatLongOctalOrBinaryBytes(0x0102030405L, shortBinary, 0, shortBinary.length);
        assertThat(TarUtils.parseOctalOrBinary(shortBinary, 0, shortBinary.length)).isEqualTo(0x0102030405L);
        final byte[] shortNegativeBinary = new byte[8];
        TarUtils.formatLongOctalOrBinaryBytes(-123L, shortNegativeBinary, 0, shortNegativeBinary.length);
        assertThat(TarUtils.parseOctalOrBinary(shortNegativeBinary, 0, shortNegativeBinary.length)).isEqualTo(-123L);
        final byte[] header = new byte[512];
        final TarArchiveEntry entry = new TarArchiveEntry("wide-\uD83D\uDE00-name");
        entry.setSize(0);
        entry.writeEntryHeader(header,
                org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("UTF-8"), true);
        assertThat(new TarArchiveEntry(header).getName()).isNotEmpty();
    }

    @Test
    void streamAndFileApisReadARealTarArchive() throws Exception {
        final Path source = Files.createTempFile("tar-source", ".txt");
        Files.writeString(source, "tar-data", StandardCharsets.UTF_8);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream output = new TarArchiveOutputStream(bytes, "UTF-8")) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            output.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            output.setAddPaxHeadersForNonAsciiNames(true);
            final ArchiveEntry fileEntry = output.createArchiveEntry(source.toFile(), "file.txt");
            final ArchiveEntry pathEntry = output.createArchiveEntry(source, "path.txt");
            output.putArchiveEntry(fileEntry);
            output.write("tar-data".getBytes(StandardCharsets.UTF_8), 0, 8);
            output.closeArchiveEntry();
            output.putArchiveEntry(pathEntry);
            output.write("pathdatx".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
            output.flush();
            assertThat(output.getRecordSize()).isEqualTo(512);
            assertThat(output.getBytesWritten()).isPositive();
            output.finish();
        }
        final byte[] archive = bytes.toByteArray();
        try (TarArchiveOutputStream one = new TarArchiveOutputStream(new ByteArrayOutputStream());
             TarArchiveOutputStream two = new TarArchiveOutputStream(new ByteArrayOutputStream(), 512);
             TarArchiveOutputStream three = new TarArchiveOutputStream(new ByteArrayOutputStream(), 512, 512);
             TarArchiveOutputStream four = new TarArchiveOutputStream(new ByteArrayOutputStream(), 512, 512, "UTF-8")) {
            assertThat(one.getRecordSize()).isEqualTo(512);
            assertThat(two.getRecordSize()).isEqualTo(512);
            assertThat(three.getRecordSize()).isEqualTo(512);
            assertThat(four.getRecordSize()).isEqualTo(512);
        }
        try (TarArchiveInputStream one = new TarArchiveInputStream(new ByteArrayInputStream(archive));
             TarArchiveInputStream two = new TarArchiveInputStream(new ByteArrayInputStream(archive), true);
             TarArchiveInputStream three = new TarArchiveInputStream(new ByteArrayInputStream(archive), 512);
             TarArchiveInputStream four = new TarArchiveInputStream(new ByteArrayInputStream(archive), 512, 512);
             TarArchiveInputStream five = new TarArchiveInputStream(new ByteArrayInputStream(archive), 512, 512, "UTF-8");
             TarArchiveInputStream six = new TarArchiveInputStream(new ByteArrayInputStream(archive), 512, "UTF-8");
             TarArchiveInputStream seven = new TarArchiveInputStream(new ByteArrayInputStream(archive), "UTF-8")) {
            assertThat(one.getRecordSize()).isEqualTo(512);
            assertThat(seven.getRecordSize()).isEqualTo(512);
        }
        assertThat(TarArchiveInputStream.matches(archive, Math.min(512, archive.length))).isTrue();
        final TarArchiveInputStream input = new TarArchiveInputStream(new ByteArrayInputStream(archive), 512, 512, "UTF-8", true);
        assertThat(input.getRecordSize()).isEqualTo(512);
        final boolean markSupported = input.markSupported();
        input.mark(64);
        final TarArchiveEntry first = input.getNextTarEntry();
        assertThat(first.getName()).isEqualTo("file.txt");
        assertThat(input.getCurrentEntry()).isSameAs(first);
        assertThat(input.canReadEntryData(first)).isTrue();
        final byte[] data = new byte[8];
        assertThat(input.read(data, 0, data.length)).isEqualTo(8);
        assertThat(input.available()).isGreaterThanOrEqualTo(0);
        assertThat(input.skip(0)).isZero();
        input.reset();
        assertThat(markSupported).isFalse();
        assertThat(input.getNextEntry()).isNotNull();
        input.close();

        final Path archivePath = Files.createTempFile("tar-file", ".tar");
        Files.write(archivePath, archive);
        try (TarFile byPath = new TarFile(archivePath);
             TarFile byFile = new TarFile(archivePath.toFile());
             TarFile byBytes = new TarFile(archive);
             TarFile byEncoding = new TarFile(archive, "UTF-8")) {
            assertThat(byPath.getEntries()).hasSize(2);
            assertThat(byFile.getEntries()).hasSize(2);
            assertThat(byBytes.getEntries()).hasSize(2);
            assertThat(byEncoding.getEntries()).hasSize(2);
            final TarArchiveEntry entry = byPath.getEntries().get(0);
            try (InputStream entryInput = byPath.getInputStream(entry)) {
                assertThat(entryInput.readAllBytes()).containsExactly("tar-data".getBytes(StandardCharsets.UTF_8));
            }
        }
        Files.deleteIfExists(source);
        Files.deleteIfExists(archivePath);
    }

    @Test
    void globalPaxAndStarHeadersReachBothPublicTarReaders() throws Exception {
        for (final String name : new String[] {"pax_gnu_sparse.tar", "preepoch-star.tar",
                "archive_with_trailer.tar", "oldgnu_extended_sparse.tar"}) {
            final Path archive = fixture(name);
            try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(archive))) {
                TarArchiveEntry entry;
                while ((entry = input.getNextTarEntry()) != null) {
                    assertThat(entry.getName()).isNotNull();
                    input.readAllBytes();
                }
            }
            try (TarFile input = new TarFile(archive)) {
                for (final TarArchiveEntry entry : input.getEntries()) {
                    try (InputStream content = input.getInputStream(entry)) {
                        content.readAllBytes();
                    }
                }
            }
        }
    }

    private static String paxRecord(final String key, final String value) {
        final String body = key + "=" + value + "\n";
        int length = body.length() + 3;
        while (Integer.toString(length).length() + 1 + body.length() != length) {
            length++;
        }
        return length + " " + body;
    }

    private static Path fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve("forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted").resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }
}
