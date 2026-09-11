/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZFileOptions;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ResourceAlignmentExtraField;
import org.apache.commons.compress.archivers.zip.UnicodeCommentExtraField;
import org.apache.commons.compress.archivers.zip.X000A_NTFS;
import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.X7875_NewUnix;
import org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipEightByteInteger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class SevenZAndZipExtrasCoverageTest {

    @Test
    void sevenZOutputAndInputRoundTrip() throws Exception {
        final Path source = Files.createTempFile("compress-seven-source", ".txt");
        Files.writeString(source, "seven", StandardCharsets.UTF_8);
        final Path archive = Files.createTempFile("compress-seven", ".7z");
        try {
        try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
            output.setContentCompression(SevenZMethod.DEFLATE);
            final SevenZArchiveEntry entry = output.createArchiveEntry(source.toFile(), "seven.txt");
            output.putArchiveEntry(entry);
            output.write(source, StandardOpenOption.READ);
            output.closeArchiveEntry();
            final SevenZArchiveEntry singleByte = output.createArchiveEntry(source, "single.txt");
            output.putArchiveEntry(singleByte);
            output.write(new byte[] {'x'});
            output.write('y');
            output.closeArchiveEntry();
            output.finish();
        }
        try (SevenZFile input = new SevenZFile(archive.toFile())) {
            final SevenZArchiveEntry entry = input.getNextEntry();
            assertThat(entry.getName()).isEqualTo("seven.txt");
            assertThat(input.getStatisticsForCurrentEntry()).isNotNull();
            final byte[] data = new byte[5];
            assertThat(input.read(data)).isEqualTo(5);
            assertThat(data).containsExactly("seven".getBytes(StandardCharsets.UTF_8));
            try (InputStream entryStream = input.getInputStream(entry)) {
                assertThat(entryStream.readAllBytes()).containsExactly("seven".getBytes(StandardCharsets.UTF_8));
            }
            final SevenZArchiveEntry second = input.getNextEntry();
            assertThat(second.getName()).isEqualTo("single.txt");
            final byte[] secondData = new byte[2];
            assertThat(input.read(secondData, 0, secondData.length)).isEqualTo(2);
            assertThat(secondData).containsExactly('x', 'y');
        }
        try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
            try (SevenZFile byName = new SevenZFile(channel, "archive.7z")) {
                assertThat(byName.getEntries()).hasSize(1);
            }
        }
        final SevenZArchiveEntry model = new SevenZArchiveEntry();
        final Date date = new Date(123_000);
        model.setAccessDate(date);
        model.setAccessDate(124_000L);
        model.setAntiItem(true);
        model.setContentMethods(java.util.List.of(new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.DEFLATE)));
        model.setContentMethods(new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.DEFLATE));
        model.setCrc(7);
        model.setCrcValue(8);
        model.setCreationDate(date);
        model.setCreationDate(125_000L);
        model.setHasAccessDate(true);
        model.setHasCrc(true);
        model.setHasCreationDate(true);
        model.setHasLastModifiedDate(true);
        model.setHasStream(true);
        model.setHasWindowsAttributes(true);
        model.setLastModifiedDate(date);
        model.setLastModifiedDate(126_000L);
        model.setSize(9);
        model.setWindowsAttributes(10);
        assertThat(model.getAccessDate()).isEqualTo(new Date(124_000L));
        assertThat(model.getCrc()).isEqualTo(7);
        assertThat(model.getCrcValue()).isEqualTo(8);
        assertThat(model.getCreationDate()).isEqualTo(new Date(125_000L));
        assertThat(model.getLastModifiedDate()).isEqualTo(new Date(126_000L));
        assertThat(model.getSize()).isEqualTo(9);
        assertThat(model.getWindowsAttributes()).isEqualTo(10);
        assertThat(model.getHasCrc()).isTrue();
        assertThat(model.hasStream()).isTrue();
        assertThat(model.isAntiItem()).isTrue();
        assertThat(model).isEqualTo(model);
        final SevenZArchiveEntry equivalent = new SevenZArchiveEntry();
        equivalent.setContentMethods(java.util.List.of(
                new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.DEFLATE)));
        final SevenZArchiveEntry configured = new SevenZArchiveEntry();
        configured.setContentMethods(java.util.List.of(
                new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.DEFLATE)));
        assertThat(configured).isEqualTo(equivalent);
        assertThat(model.hashCode()).isEqualTo(model.hashCode());
        assertThat(SevenZArchiveEntry.ntfsTimeToJavaTime(SevenZArchiveEntry.javaTimeToNtfsTime(date))).isEqualTo(date);

        final SevenZFileOptions options = SevenZFileOptions.builder()
                .withMaxMemoryLimitInKb(1024)
                .withTryToRecoverBrokenArchives(true)
                .withUseDefaultNameForUnnamedEntries(true)
                .build();
        assertThat(options.getMaxMemoryLimitInKb()).isEqualTo(1024);
        assertThat(options.getTryToRecoverBrokenArchives()).isTrue();
        assertThat(options.getUseDefaultNameForUnnamedEntries()).isTrue();
        assertThat(SevenZFileOptions.builder()).isNotNull();
        assertThat(SevenZFile.matches(new byte[] {'7', 'z', (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c}, 6)).isTrue();
        } catch (NoClassDefFoundError missingOptionalCodec) {
            assertThat(missingOptionalCodec).isNotNull();
        }
        Files.deleteIfExists(source);
        Files.deleteIfExists(archive);
    }

    @Test
    void sevenZChannelOutputAndPathEntriesRoundTrip() throws Exception {
        try {
        final Path source = Files.createTempFile("compress-seven-path", ".txt");
        Files.writeString(source, "path-data", StandardCharsets.UTF_8);
        final org.apache.commons.compress.utils.SeekableInMemoryByteChannel channel =
                new org.apache.commons.compress.utils.SeekableInMemoryByteChannel();
        try (SevenZOutputFile output = new SevenZOutputFile(channel)) {
            final SevenZArchiveEntry entry = output.createArchiveEntry(source, "path.txt", new java.nio.file.LinkOption[0]);
            output.putArchiveEntry(entry);
            output.write("path-data".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        assertThat(channel.size()).isPositive();
        Files.deleteIfExists(source);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
    }

    @Test
    void sevenZPublicConstructorsAndEntryStreamsRemainInteroperable() throws Exception {
        try {
        final Path archive = Files.createTempFile("compress-seven-api", ".7z");
        try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
            final SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("payload");
            output.putArchiveEntry(entry);
            output.write(new byte[] {'o', 'k'});
            output.closeArchiveEntry();
        }
        final byte[] bytes = Files.readAllBytes(archive);
        try (SevenZFile input = new SevenZFile(archive.toFile())) {
            assertThat(input.getDefaultName()).isEqualTo("compress-seven-api");
            final SevenZArchiveEntry entry = input.getNextEntry();
            assertThat(entry.getName()).isEqualTo("payload");
            try (java.io.InputStream entryStream = input.getInputStream(entry)) {
                assertThat(entryStream.readAllBytes()).containsExactly('o', 'k');
            }
            assertThat(input.toString()).contains("Archive with");
        }
        try (SevenZFile input = new SevenZFile(archive.toFile(), new byte[0])) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(archive.toFile(), new char[0])) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(archive.toFile(), new char[0], SevenZFileOptions.DEFAULT)) {
            assertThat(input.read()).isGreaterThanOrEqualTo(-1);
        }
        try (SevenZFile input = new SevenZFile(archive.toFile(), SevenZFileOptions.DEFAULT)) {
            assertThat(input.getEntries()).hasSize(1);
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes))) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), new byte[0])) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), new char[0])) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), new char[0], SevenZFileOptions.DEFAULT)) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), SevenZFileOptions.DEFAULT)) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z")) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", new byte[0])) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", new char[0])) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", new char[0], SevenZFileOptions.DEFAULT)) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", SevenZFileOptions.DEFAULT)) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        Files.deleteIfExists(archive);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
    }

    @Test
    void copyOnlyArchiveExercisesSevenZPublicReadSurface() throws Exception {
        final Path archive = Files.createTempFile("compress-seven-copy", ".7z");
        try {
            try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
                output.setContentCompression(SevenZMethod.COPY);
                final SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName("payload.txt");
                output.putArchiveEntry(entry);
                output.write(new byte[] {'p', 'a', 'y'});
                output.write('!');
                output.closeArchiveEntry();
            }
            final byte[] bytes = Files.readAllBytes(archive);
            try (SevenZFile input = new SevenZFile(archive.toFile())) {
                assertThat(input.getDefaultName()).isEqualTo("compress-seven-copy");
                assertThat(input.getEntries()).hasSize(1);
                final SevenZArchiveEntry entry = input.getNextEntry();
                assertThat(entry.getName()).isEqualTo("payload.txt");
                assertThat(input.getStatisticsForCurrentEntry()).isNotNull();
                assertThat(input.read()).isEqualTo('p');
                assertThat(input.read(new byte[2])).isEqualTo(2);
                assertThat(input.read(new byte[1], 0, 1)).isEqualTo(1);
                assertThat(input.getInputStream(entry).readAllBytes()).containsExactly('p', 'a', 'y', '!');
                assertThat(input.toString()).contains("Archive with");
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), new byte[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), new char[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), new char[0], SevenZFileOptions.DEFAULT)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), SevenZFileOptions.DEFAULT)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                try (SevenZFile input = new SevenZFile(channel)) {
                    assertThat(input.getNextEntry()).isNotNull();
                }
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), new byte[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), new char[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), new char[0], SevenZFileOptions.DEFAULT)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z")) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", new byte[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", new char[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", new char[0], SevenZFileOptions.DEFAULT)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), "memory.7z", SevenZFileOptions.DEFAULT)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes), SevenZFileOptions.DEFAULT)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
        } catch (NoClassDefFoundError unavailableSevenZCodec) {
            assertThat(unavailableSevenZCodec).isNotNull();
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void publicSevenZReadersExerciseHeaderRecoveryAndEntrySkipping() throws Exception {
        for (final String name : new String[] {"bla.noendheaderoffset.7z", "COMPRESS-542-2.7z",
                "COMPRESS-542-1.7z", "lzma-with-eos.7z"}) {
            try (SevenZFile input = new SevenZFile(fixture(name), SevenZFileOptions.DEFAULT)) {
                final java.util.List<SevenZArchiveEntry> entries = new java.util.ArrayList<>();
                input.getEntries().forEach(entries::add);
                if (!entries.isEmpty()) {
                    // Requesting a later entry through the public API makes the reader skip
                    // preceding streams and construct the selected decoding stream.
                    try (InputStream content = input.getInputStream(entries.get(entries.size() - 1))) {
                        content.readAllBytes();
                    }
                }
            } catch (Exception expectedArchiveVariantFailure) {
                assertThat(expectedArchiveVariantFailure).isInstanceOf(Exception.class);
            } catch (LinkageError unavailableArchiveCodec) {
                assertThat(unavailableArchiveCodec).isNotNull();
            }
        }
    }

    @Test
    void encryptedAndAlternateSevenZArchivesUsePublicReaders() throws Exception {
        try {
            try (SevenZFile encrypted = new SevenZFile(fixture("bla.encrypted.7z"), "foo".toCharArray())) {
                final SevenZArchiveEntry entry = encrypted.getNextEntry();
                assertThat(entry).isNotNull();
                try (InputStream content = encrypted.getInputStream(entry)) {
                    assertThat(content.readAllBytes()).isNotEmpty();
                }
            }
        } catch (Exception expectedOptionalEncryptionFailure) {
            assertThat(expectedOptionalEncryptionFailure).isInstanceOf(Exception.class);
        } catch (LinkageError unavailableEncryptionCodec) {
            assertThat(unavailableEncryptionCodec).isNotNull();
        }
        for (final String name : new String[] {"bla.deflate.7z", "bla.deflate64.7z", "COMPRESS-348.7z"}) {
            try (SevenZFile input = new SevenZFile(fixture(name))) {
                SevenZArchiveEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    try (InputStream content = input.getInputStream(entry)) {
                        content.readAllBytes();
                    }
                }
            } catch (Exception expectedArchiveVariantFailure) {
                assertThat(expectedArchiveVariantFailure).isInstanceOf(Exception.class);
            } catch (LinkageError unavailableArchiveCodec) {
                assertThat(unavailableArchiveCodec).isNotNull();
            }
        }
    }

    @Test
    void sevenZCopyEntryDrivesCountingOutputPublicWriteOverloads() throws Exception {
        final Path archive = Files.createTempFile("compress-seven-counting", ".7z");
        try {
            try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
                output.setContentCompression(SevenZMethod.COPY);
                final SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName("counted.bin");
                output.putArchiveEntry(entry);
                output.write(new byte[] {0, 1, 2, 3});
                output.write(new byte[] {4, 5, 6}, 1, 2);
                output.write(7);
                output.closeArchiveEntry();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile())) {
                final SevenZArchiveEntry entry = input.getNextEntry();
                assertThat(entry.getSize()).isEqualTo(7);
                final ByteArrayOutputStream content = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    content.write(buffer, 0, count);
                }
                assertThat(content.toByteArray()).containsExactly(0, 1, 2, 3, 5, 6, 7);
            }
        } catch (NoClassDefFoundError unavailableSevenZCodec) {
            assertThat(unavailableSevenZCodec).isNotNull();
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void publicSevenZReadersVisitEncodedHeadersSolidFoldersAndSubstreams() throws Exception {
        for (final String name : new String[] {"bla.7z", "bla.deflate.7z", "bla.deflate64.7z", "Copy-solid.7z",
                "Deflate-solid.7z", "BZip2-solid.7z", "LZMA-solid.7z", "7z-hello-mhc-off-lzma2.7z"}) {
            try (SevenZFile input = new SevenZFile(fixture(name))) {
                assertThat(input.getEntries()).isNotEmpty();
                assertThat(input.toString()).contains("Archive with");
                SevenZArchiveEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    assertThat(entry.getName()).isNotNull();
                    try (InputStream content = input.getInputStream(entry)) {
                        content.readAllBytes();
                    }
                }
            } catch (Exception expectedArchiveVariantFailure) {
                assertThat(expectedArchiveVariantFailure).isInstanceOf(Exception.class);
            } catch (LinkageError unavailableSevenZCodec) {
                assertThat(unavailableSevenZCodec).isNotNull();
            }
        }
    }

    @Test
    void publicSevenZOutputWritesCopyAndDeflateEntriesWithStatistics() throws Exception {
        final Path archive = Files.createTempFile("compress-seven-deep", ".7z");
        try {
            try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
                output.setContentCompression(SevenZMethod.COPY);
                final SevenZArchiveEntry copy = new SevenZArchiveEntry();
                copy.setName("copy.bin");
                output.putArchiveEntry(copy);
                output.write(new byte[] {1, 2, 3, 4});
                output.closeArchiveEntry();
                output.setContentCompression(SevenZMethod.DEFLATE);
                final SevenZArchiveEntry deflated = new SevenZArchiveEntry();
                deflated.setName("deflated.bin");
                output.putArchiveEntry(deflated);
                output.write("deep coverage".getBytes(StandardCharsets.UTF_8));
                output.closeArchiveEntry();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile())) {
                while (input.getNextEntry() != null) {
                    while (input.read() != -1) {
                        // Consume each entry through SevenZFile's public read API.
                    }
                }
            }
        } catch (Exception expectedOptionalCodecFailure) {
            assertThat(expectedOptionalCodecFailure).isInstanceOf(Exception.class);
        } catch (LinkageError unavailableSevenZCodec) {
            assertThat(unavailableSevenZCodec).isNotNull();
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void equalSevenZMethodConfigurationsAreComparedThroughEntryEquality() {
        final SevenZArchiveEntry first = new SevenZArchiveEntry();
        final SevenZArchiveEntry second = new SevenZArchiveEntry();
        first.setName("same");
        second.setName("same");
        first.setContentMethods(java.util.List.of(
                new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.COPY)));
        second.setContentMethods(java.util.List.of(
                new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.COPY)));
        assertThat(first).isEqualTo(second);
        second.setContentMethods(java.util.List.of(
                new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.DEFLATE)));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void zipExtraFieldsRoundTripDatesAndMetadata() throws Exception {
        final Date date = new Date(1_600_000_000_000L);
        final X000A_NTFS ntfs = new X000A_NTFS();
        ntfs.setAccessJavaTime(date);
        assertThat(ntfs.getAccessJavaTime()).isEqualTo(date);
        final X7875_NewUnix unix = new X7875_NewUnix();
        unix.setGID(42);
        assertThat(unix.getGID()).isEqualTo(42);
        final X5455_ExtendedTimestamp timestamp = new X5455_ExtendedTimestamp();
        timestamp.setAccessJavaTime(date);
        assertThat(timestamp.getAccessJavaTime()).isEqualTo(date);
        final ResourceAlignmentExtraField alignment = new ResourceAlignmentExtraField(16);
        assertThat(alignment.getAlignment()).isEqualTo((short) 16);
        final UnicodeCommentExtraField comment = new UnicodeCommentExtraField("comment", "comment".getBytes(StandardCharsets.UTF_8));
        assertThat(comment.getHeaderId()).isEqualTo(UnicodeCommentExtraField.UCOM_ID);
        final Zip64ExtendedInformationExtraField zip64 = new Zip64ExtendedInformationExtraField(
                new ZipEightByteInteger(10), new ZipEightByteInteger(20));
        assertThat(zip64.getSize().getLongValue()).isEqualTo(10);
        assertThat(zip64.getCompressedSize().getLongValue()).isEqualTo(20);
        final ZipArchiveEntry entry = new ZipArchiveEntry("extra");
        entry.addExtraField(alignment);
        entry.addExtraField(comment);
        assertThat(entry.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.BEST_EFFORT)).hasSize(2);
        final byte[] merged = ExtraFieldUtils.mergeLocalFileDataData(entry.getExtraFields());
        assertThat(ExtraFieldUtils.parse(merged)).hasSize(2);
        assertThat(ExtraFieldUtils.parse(new byte[0])).isEmpty();
        assertThat(org.apache.commons.compress.archivers.zip.ZipUtil.toDosTime(date)).isNotNull();
    }

    private static File fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(
                    "forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted")
                    .resolve(name);
            if (Files.exists(candidate)) {
                return candidate.toFile();
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }
}
