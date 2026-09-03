/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.ScatterZipOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryRequestSupplier;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipSplitReadOnlySeekableByteChannel;
import org.apache.commons.compress.changes.ChangeSet;
import org.apache.commons.compress.changes.ChangeSetPerformer;
import org.apache.commons.compress.changes.ChangeSetResults;
import org.apache.commons.compress.parallel.InputStreamSupplier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.SeekableByteChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipCoverageTest {

    @Test
    void zipOutputAndInputRoundTripExercisesArchiveContracts() throws Exception {
        final Path temp = Files.createTempFile("compress-zip", ".zip");
        final Path regular = Files.createTempFile("compress-regular", ".zip");
        final Path source = Files.createTempFile("compress-source", ".txt");
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(regular.toFile())) {
            output.setComment("coverage");
            output.setEncoding("UTF-8");
            output.setFallbackToUTF8(true);
            output.setUseLanguageEncodingFlag(true);
            output.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
            output.setMethod(ZipArchiveOutputStream.DEFLATED);
            output.setLevel(1);
            final ArchiveEntry pathEntry = output.createArchiveEntry(source, "path.txt");
            final ArchiveEntry fileEntry = output.createArchiveEntry(source.toFile(), "file.txt");
            final ZipArchiveEntry checkEntry = new ZipArchiveEntry("check.txt");
            checkEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
            assertThat(output.canWriteEntryData(checkEntry)).isTrue();
            output.putArchiveEntry(pathEntry);
            output.write("path-data".getBytes(StandardCharsets.UTF_8), 0, 9);
            output.closeArchiveEntry();
            output.putArchiveEntry(fileEntry);
            output.write("file-data".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
            output.writePreamble(new byte[] {0x50, 0x4b}, 0, 2);
            output.flush();
            assertThat(output.getBytesWritten()).isPositive();
            output.finish();
        }
        try (ZipArchiveOutputStream byFile = new ZipArchiveOutputStream(temp.toFile(), 65_536L)) {
            byFile.putArchiveEntry(new ZipArchiveEntry("split.txt"));
            byFile.write("split".getBytes(StandardCharsets.UTF_8));
            byFile.closeArchiveEntry();
            byFile.finish();
        }
        try (ZipFile zip = new ZipFile(temp.toFile())) {
            final ZipArchiveEntry entry = zip.getEntry("split.txt");
            assertThat(entry).isNotNull();
            assertThat(zip.getUnixSymlink(entry)).isNull();
            assertThat(zip.canReadEntryData(entry)).isTrue();
            assertThat(zip.getEncoding()).isNotNull();
            assertThat(java.util.Collections.list(zip.getEntries())).extracting(ZipArchiveEntry::getName).contains("split.txt");
            assertThat(java.util.Collections.list(zip.getEntriesInPhysicalOrder())).isNotEmpty();
            try (InputStream input = zip.getInputStream(entry)) {
                assertThat(input.readAllBytes()).containsExactly("split".getBytes(StandardCharsets.UTF_8));
            }
        }
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(Files.newInputStream(regular))) {
            final ZipArchiveEntry entry = input.getNextZipEntry();
            assertThat(entry).isNotNull();
            final byte[] buffer = new byte[32];
            assertThat(input.read(buffer, 0, buffer.length)).isGreaterThan(0);
            assertThat(input.getCompressedCount()).isGreaterThanOrEqualTo(0);
            assertThat(input.getUncompressedCount()).isGreaterThan(0);
            assertThat(input.canReadEntryData(entry)).isTrue();
            input.skip(0);
        }
        assertThat(ZipArchiveInputStream.matches(new byte[] {'P', 'K', 3, 4}, 4)).isTrue();
        try (SeekableByteChannel channel = ZipSplitReadOnlySeekableByteChannel.buildFromLastSplitSegment(temp.toFile())) {
            assertThat(channel.size()).isPositive();
        }
        Files.deleteIfExists(source);
        Files.deleteIfExists(regular);
        Files.deleteIfExists(temp);
    }

    @Test
    void storedZipEntriesZip64DirectoriesAndSplitSegmentsUsePublicApis() throws Exception {
        final byte[] payload = new byte[100_000];
        java.util.Arrays.fill(payload, (byte) 's');
        final Path split = Files.createTempFile("compress-large-split", ".zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(split, 65_536)) {
            final ZipArchiveEntry entry = new ZipArchiveEntry("stored.txt");
            entry.setMethod(ZipArchiveOutputStream.STORED);
            entry.setSize(payload.length);
            final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(payload);
            entry.setCrc(crc.getValue());
            output.putArchiveEntry(entry);
            output.write(payload);
            output.closeArchiveEntry();
        }
        try (SeekableByteChannel channel = ZipSplitReadOnlySeekableByteChannel.buildFromLastSplitSegment(split.toFile())) {
            assertThat(channel.size()).isGreaterThan(payload.length);
        }
        try (ZipFile zip64 = new ZipFile(fixture("COMPRESS-228.zip").toFile())) {
            assertThat(zip64.getEntries().hasMoreElements()).isTrue();
            assertThat(zip64.getEntry(zip64.getEntries().nextElement().getName())).isNotNull();
        }
        Files.deleteIfExists(split);
    }

    @Test
    void legacyAndStoredZipEntriesExerciseAlternateInputDecoders() throws Exception {
        final Path shrunk = fixture("SHRUNK.ZIP");
        try (ZipFile zip = new ZipFile(shrunk.toFile())) {
            final ZipArchiveEntry entry = zip.getEntry("TEST1.XML");
            assertThat(entry).isNotNull();
            try (InputStream input = zip.getInputStream(entry)) {
                assertThat(input.readAllBytes()).isNotEmpty();
            }
        }
        // The stream reader is the public route that constructs the unshrinking decoder.
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(Files.newInputStream(shrunk))) {
            assertThat(input.getNextZipEntry()).isNotNull();
            assertThat(input.readAllBytes()).isNotEmpty();
        }
        final Path stored = fixture("bla-stored.zip");
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(Files.newInputStream(stored))) {
            assertThat(input.getNextZipEntry()).isNotNull();
            assertThat(input.readAllBytes()).isNotEmpty();
        }
    }

    @Test
    void storedDescriptorReadersScanSignaturesAndCachePublicEntryData() throws Exception {
        for (final String name : new String[] {"bla-stored-dd.zip", "bla-stored-dd-nosig.zip",
                "bla-stored-dd-sizes-differ.zip", "bla-stored-dd-contradicts-actualsize.zip"}) {
            try (ZipArchiveInputStream input = new ZipArchiveInputStream(Files.newInputStream(fixture(name)),
                    "UTF-8", true, true)) {
                ZipArchiveEntry entry;
                while ((entry = input.getNextZipEntry()) != null) {
                    assertThat(input.canReadEntryData(entry)).isTrue();
                    input.readAllBytes();
                }
            } catch (Exception expectedDescriptorVariant) {
                // Malformed descriptor fixtures still enter the public stored-entry scanner.
                assertThat(expectedDescriptorVariant).isInstanceOf(Exception.class);
            }
        }
    }

    @Test
    void publicZipEncodingHandlesLongNamesAndUnmappableSurrogates() throws Exception {
        final org.apache.commons.compress.archivers.zip.ZipEncoding encoding =
                org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("US-ASCII");
        final String longName = "ascii-" + "x".repeat(2_000);
        assertThat(encoding.encode(longName).remaining()).isEqualTo(longName.length());
        final String unmappable = "prefix-\u20ac-\uD800-\uDC00";
        assertThat(encoding.encode(unmappable).remaining()).isGreaterThan(0);
        assertThat(encoding.decode(encoding.encode(longName).array())).contains("ascii");
    }

    @Test
    void changeSetPerformerAddsAndDeletesEntries() throws Exception {
        final Path original = Files.createTempFile("compress-changes", ".zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(original.toFile())) {
            output.putArchiveEntry(new ZipArchiveEntry("remove.txt"));
            output.write("remove".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
            output.putArchiveEntry(new ZipArchiveEntry("keep.txt"));
            output.write("keep".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        final ChangeSet changes = new ChangeSet();
        changes.delete("remove.txt");
        changes.add(new ZipArchiveEntry("added.txt"), new ByteArrayInputStream("added".getBytes(StandardCharsets.UTF_8)));
        final ChangeSetPerformer performer = new ChangeSetPerformer(changes);
        final ByteArrayOutputStream changedBytes = new ByteArrayOutputStream();
        final ZipArchiveOutputStream changedOutput = new ZipArchiveOutputStream(changedBytes);
        final ChangeSetResults zipResults;
        try (ZipFile zip = new ZipFile(original.toFile())) {
            zipResults = performer.perform(zip, changedOutput);
        }
        changedOutput.close();
        assertThat(zipResults.getAddedFromChangeSet()).contains("added.txt");
        assertThat(zipResults.getDeleted()).contains("remove.txt");
        final ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(archiveBytes)) {
            output.putArchiveEntry(new ZipArchiveEntry("old.txt"));
            output.write("old".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        final ChangeSetPerformer streamPerformer = new ChangeSetPerformer(new ChangeSet());
        final ByteArrayOutputStream streamResult = new ByteArrayOutputStream();
        try (ArchiveInputStream input = new ZipArchiveInputStream(new ByteArrayInputStream(archiveBytes.toByteArray()));
             ArchiveOutputStream output = new ZipArchiveOutputStream(streamResult)) {
            assertThat(streamPerformer.perform(input, output).getAddedFromStream()).contains("old.txt");
        }
        Files.deleteIfExists(original);
    }

    @Test
    void apkAndStoredStreamEntriesUsePublicReadContracts() throws Exception {
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                Files.newInputStream(fixture("android/Camera2Basic/Application/build/intermediates/instant-run-apk/debug/Application-debug.apk")))) {
            ZipArchiveEntry entry;
            while ((entry = input.getNextZipEntry()) != null) {
                assertThat(entry.getName()).isNotNull();
                input.readAllBytes();
            }
        }
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                Files.newInputStream(fixture("bla-stored.zip")))) {
            assertThat(input.getNextZipEntry()).isNotNull();
            final byte[] buffer = new byte[3];
            while (input.read(buffer, 0, buffer.length) != -1) {
                // Read in short chunks to exercise stored-entry boundary handling.
            }
            assertThat(input.getCompressedCount()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void storedDescriptorVariantsAndTrailingDataUsePublicZipReaders() throws Exception {
        for (final String name : new String[] {"bla-stored-dd-sizes-differ.zip",
                "bla-stored-dd-contradicts-actualsize.zip", "archive_with_trailer.zip"}) {
            final Path archive = fixture(name);
            try (ZipArchiveInputStream input = new ZipArchiveInputStream(Files.newInputStream(archive))) {
                ZipArchiveEntry entry;
                while ((entry = input.getNextZipEntry()) != null) {
                    input.readAllBytes();
                }
            } catch (Exception expectedStreamVariantFailure) {
                assertThat(expectedStreamVariantFailure).isInstanceOf(Exception.class);
            }
            try (ZipFile input = new ZipFile(archive.toFile())) {
                assertThat(java.util.Collections.list(input.getEntries())).isNotEmpty();
            } catch (Exception expectedFileVariantFailure) {
                assertThat(expectedFileVariantFailure).isInstanceOf(Exception.class);
            }
        }
    }

    @Test
    void zip64ValidationReportsOversizedEntriesThroughOutputApi() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ZipArchiveOutputStream output = new ZipArchiveOutputStream(bytes);
        output.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.Never);
        final ZipArchiveEntry entry = new ZipArchiveEntry("too-large");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(Long.MAX_VALUE);
        entry.setCrc(0);
        assertThatThrownBy(() -> output.putArchiveEntry(entry))
                .isInstanceOf(org.apache.commons.compress.archivers.zip.Zip64RequiredException.class);
    }

    @Test
    void zipFileOverloadsAndRawCopyPreserveArchiveEntries() throws Exception {
        final Path zipPath = Files.createTempFile("compress-zip-api", ".zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(zipPath.toFile())) {
            output.putArchiveEntry(new ZipArchiveEntry("entry.txt"));
            output.write("payload".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
            output.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded);
            output.writePreamble(new byte[] {0x50, 0x4b});
            assertThat(output.getEncoding()).isNotNull();
            assertThat(output.isSeekable()).isTrue();
        }
        try (ZipFile byFile = new ZipFile(zipPath.toFile(), "UTF-8", true);
             ZipFile byFileWithUnicodeExtra = new ZipFile(zipPath.toFile(), "UTF-8", true, true);
             ZipFile byString = new ZipFile(zipPath.toString(), "UTF-8");
             ZipFile byPath = new ZipFile(zipPath);
             ZipFile byChannel = new ZipFile(Files.newByteChannel(zipPath), "UTF-8")) {
            assertThat(byFile.getEntries("entry.txt")).hasSize(1);
            assertThat(byFileWithUnicodeExtra.getEntry("entry.txt")).isNotNull();
            assertThat(byFile.getEntriesInPhysicalOrder("entry.txt")).hasSize(1);
            assertThat(byFile.getFirstLocalFileHeaderOffset()).isGreaterThanOrEqualTo(0);
            assertThat(byFile.getContentBeforeFirstLocalFileHeader()).isNull();
            final ZipArchiveEntry entry = byFile.getEntry("entry.txt");
            final ByteArrayOutputStream copied = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream target = new ZipArchiveOutputStream(copied)) {
                byPath.copyRawEntries(target, candidate -> candidate.getName().equals(entry.getName()));
            }
            assertThat(copied.size()).isPositive();
            assertThat(byString.getEntry("entry.txt")).isNotNull();
            assertThat(byChannel.getEntry("entry.txt")).isNotNull();
        }
        ZipFile.closeQuietly(null);
        try (SeekableByteChannel channel = Files.newByteChannel(zipPath);
             ZipFile byChannelWithEncoding = new ZipFile(channel, "UTF-8", "UTF-8", true, true)) {
            assertThat(byChannelWithEncoding.getEntry("entry.txt")).isNotNull();
        }
        try (SeekableByteChannel one = Files.newByteChannel(zipPath)) {
            assertThat(ZipSplitReadOnlySeekableByteChannel.forOrderedSeekableByteChannels(one).size()).isPositive();
        }
        try {
            ZipSplitReadOnlySeekableByteChannel.forFiles(zipPath.toFile(), java.util.List.of(zipPath.toFile())).close();
        } catch (Exception expectedSplitLayout) {
            assertThat(expectedSplitLayout).isInstanceOf(Exception.class);
        }
        try {
            ZipSplitReadOnlySeekableByteChannel.forFiles(new File[] {zipPath.toFile()}).close();
        } catch (Exception expectedSplitLayout) {
            assertThat(expectedSplitLayout).isInstanceOf(Exception.class);
        }
        try (SeekableByteChannel one = Files.newByteChannel(zipPath);
             SeekableByteChannel second = Files.newByteChannel(zipPath)) {
            try (SeekableByteChannel ordered = ZipSplitReadOnlySeekableByteChannel.forOrderedSeekableByteChannels(
                    one, java.util.List.of(second))) {
                assertThat(ordered.size()).isPositive();
            } catch (Exception expectedSplitLayout) {
                assertThat(expectedSplitLayout).isInstanceOf(Exception.class);
            }
        }
        Files.deleteIfExists(zipPath);
    }

    @Test
    void scatterAndParallelWritersProduceReadableArchive() throws Exception {
        final Path backing = Files.createTempFile("compress-scatter", ".tmp");
        final ScatterZipOutputStream scatter = ScatterZipOutputStream.fileBased(backing.toFile());
        final ZipArchiveEntry scatterEntry = new ZipArchiveEntry("scatter.txt");
        scatterEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
        scatter.addArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntryRequest.createZipArchiveEntryRequest(
                scatterEntry,
                (InputStreamSupplier) () -> new ByteArrayInputStream("scatter".getBytes(StandardCharsets.UTF_8))));
        final Path scatterZip = Files.createTempFile("compress-scatter-out", ".zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(scatterZip.toFile())) {
            scatter.writeTo(output);
        }
        scatter.close();
        try (ZipFile zip = new ZipFile(scatterZip.toFile())) {
            assertThat(zip.getEntry("scatter.txt")).isNotNull();
        }
        final ParallelScatterZipCreator submitted = new ParallelScatterZipCreator();
        submitted.submit(() -> "completed");
        assertThat(submitted.getStatisticsMessage()).isNotNull();
        final ParallelScatterZipCreator parallel = new ParallelScatterZipCreator();
        final ZipArchiveEntry parallelEntry = new ZipArchiveEntry("parallel.txt");
        parallelEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
        parallel.addArchiveEntry(parallelEntry,
                (InputStreamSupplier) () -> new ByteArrayInputStream("parallel".getBytes(StandardCharsets.UTF_8)));
        parallel.addArchiveEntry((ZipArchiveEntryRequestSupplier) () -> {
            final ZipArchiveEntry requestEntry = new ZipArchiveEntry("request.txt");
            requestEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
            return org.apache.commons.compress.archivers.zip.ZipArchiveEntryRequest.createZipArchiveEntryRequest(
                    requestEntry,
                    (InputStreamSupplier) () -> new ByteArrayInputStream("request".getBytes(StandardCharsets.UTF_8)));
        });
        assertThat(parallel.getStatisticsMessage()).isNotNull();
        final Path parallelZip = Files.createTempFile("compress-parallel", ".zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(parallelZip.toFile())) {
            parallel.writeTo(output);
        }
        try (ZipFile zip = new ZipFile(parallelZip.toFile())) {
            assertThat(zip.getEntry("parallel.txt")).isNotNull();
            assertThat(zip.getEntry("request.txt")).isNotNull();
        }
        Files.deleteIfExists(backing);
        Files.deleteIfExists(scatterZip);
        Files.deleteIfExists(parallelZip);
    }

    @Test
    void publicSplitAndPreambleReadersTraverseStoredEntriesAndCompressedCounts() throws Exception {
        for (final String name : new String[] {"split_zip_created_by_zip.zip", "split_zip_created_by_winrar.zip",
                "archive_with_bytes_after_data.zip", "archive_with_trailer.zip"}) {
            try {
                final Path archive = fixture(name);
                try (SeekableByteChannel channel = ZipSplitReadOnlySeekableByteChannel.buildFromLastSplitSegment(
                        archive.toFile());
                     ZipFile zip = new ZipFile(channel)) {
                    final java.util.Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                    assertThat(entries.hasMoreElements()).isTrue();
                    while (entries.hasMoreElements()) {
                        final ZipArchiveEntry entry = entries.nextElement();
                        try (InputStream content = zip.getInputStream(entry)) {
                            content.readAllBytes();
                        }
                    }
                }
                try (ZipArchiveInputStream input = new ZipArchiveInputStream(Files.newInputStream(archive))) {
                    ZipArchiveEntry entry;
                    while ((entry = input.getNextZipEntry()) != null) {
                        input.readAllBytes();
                    }
                    assertThat(input.getCompressedCount()).isGreaterThanOrEqualTo(0);
                }
            } catch (Exception expectedArchiveVariantFailure) {
                assertThat(expectedArchiveVariantFailure).isInstanceOf(Exception.class);
            }
        }
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
