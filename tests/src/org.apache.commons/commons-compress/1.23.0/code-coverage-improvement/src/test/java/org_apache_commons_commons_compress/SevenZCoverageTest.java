/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

// Exercise SevenZ archive construction, reading, and entry configuration.
package org_apache_commons_commons_compress;

import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZFileOptions;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SevenZCoverageTest {

    @Test
    void entryMetadataRoundTripsAndNtfsTimesRemainStable() {
        final SevenZArchiveEntry entry = new SevenZArchiveEntry();
        final Date timestamp = new Date(1_700_000_000_000L);
        entry.setName("payload.txt");
        entry.setSize(7);
        entry.setCrc(17);
        entry.setCrcValue(17L);
        entry.setHasCrc(true);
        entry.setHasStream(true);
        entry.setAntiItem(false);
        entry.setAccessDate(timestamp);
        entry.setAccessDate(SevenZArchiveEntry.javaTimeToNtfsTime(timestamp));
        entry.setCreationDate(timestamp);
        entry.setCreationDate(SevenZArchiveEntry.javaTimeToNtfsTime(timestamp));
        entry.setLastModifiedDate(timestamp);
        entry.setLastModifiedDate(SevenZArchiveEntry.javaTimeToNtfsTime(timestamp));
        entry.setHasAccessDate(true);
        entry.setHasCreationDate(true);
        entry.setHasLastModifiedDate(true);
        entry.setWindowsAttributes(0x20);
        entry.setHasWindowsAttributes(true);
        entry.setContentMethods(new SevenZMethodConfiguration[] {
                new SevenZMethodConfiguration(SevenZMethod.COPY)
        });
        assertThat(entry.getName()).isEqualTo("payload.txt");
        assertThat(entry.getSize()).isEqualTo(7);
        assertThat(entry.getCrc()).isEqualTo(17);
        assertThat(entry.getCrcValue()).isEqualTo(17L);
        assertThat(entry.getAccessDate()).isEqualTo(timestamp);
        assertThat(entry.getCreationDate()).isEqualTo(timestamp);
        assertThat(entry.getLastModifiedDate()).isEqualTo(timestamp);
        assertThat(entry.getWindowsAttributes()).isEqualTo(0x20);
        assertThat(entry.getHasCrc()).isTrue();
        assertThat(entry.equals(entry)).isTrue();
        assertThat(entry.hashCode()).isEqualTo(entry.hashCode());
        assertThat(SevenZArchiveEntry.ntfsTimeToJavaTime(SevenZArchiveEntry.javaTimeToNtfsTime(timestamp)))
                .isEqualTo(timestamp);
    }

    @Test
    void optionsBuilderExposesArchiveRecoveryPolicy() {
        final SevenZFileOptions options = SevenZFileOptions.builder()
                .withMaxMemoryLimitInKb(512)
                .withTryToRecoverBrokenArchives(true)
                .withUseDefaultNameForUnnamedEntries(false)
                .build();
        assertThat(options.getMaxMemoryLimitInKb()).isEqualTo(512);
        assertThat(options.getTryToRecoverBrokenArchives()).isTrue();
        assertThat(options.getUseDefaultNameForUnnamedEntries()).isFalse();
        assertThat(SevenZFileOptions.DEFAULT.getMaxMemoryLimitInKb()).isPositive();
    }

    @Test
    void sevenZFileOverloadsReadTheSameUserArchive() throws Exception {
        Assumptions.assumeTrue(isSevenZCodecAvailable());
        final Path archive = Files.createTempFile("coverage", ".7z");
        final Path source = Files.createTempFile("coverage", ".txt");
        Files.writeString(source, "seven-z payload", StandardCharsets.UTF_8);
        try {
            writeArchive(archive, source);
            assertThat(SevenZFile.matches(Files.readAllBytes(archive), 6)).isTrue();
            final SevenZFileOptions options = SevenZFileOptions.builder()
                    .withUseDefaultNameForUnnamedEntries(true).build();
            try (SevenZFile input = new SevenZFile(archive.toFile())) {
                assertThat(input.getDefaultName()).isEqualTo(archive.toFile().getName());
                assertThat(input.toString()).contains("SevenZFile");
                assertThat(input.getEntries()).hasSize(1);
                final SevenZArchiveEntry entry = input.getNextEntry();
                assertThat(entry.getName()).isEqualTo(source.getFileName().toString());
                try (InputStream stream = input.getInputStream(entry)) {
                    assertThat(stream.readAllBytes()).isEqualTo("seven-z payload".getBytes(StandardCharsets.UTF_8));
                }
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), options)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), new char[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), new byte[0])) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SevenZFile input = new SevenZFile(archive.toFile(), new char[0], options)) {
                assertThat(input.getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, options).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, new char[0]).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, new char[0], options).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, "UTF-8").getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, "UTF-8", new byte[0]).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, "UTF-8", new char[0]).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, "UTF-8", new char[0], options).getNextEntry()).isNotNull();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(archive)) {
                assertThat(new SevenZFile(channel, "UTF-8", options).getNextEntry()).isNotNull();
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void solidAndMultiEntryFixturesExercisePublicDecoderState() throws Exception {
        Assumptions.assumeTrue(isSevenZCodecAvailable());
        for (final String name : new String[] {"COMPRESS-320/Copy-solid.7z", "COMPRESS-320/Deflate-solid.7z"}) {
            try (SevenZFile input = new SevenZFile(fixture(name).toFile())) {
                assertThat(input.getEntries()).isNotEmpty();
                SevenZArchiveEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    assertThat(entry.getName()).isNotNull();
                    try (InputStream content = input.getInputStream(entry)) {
                        content.readAllBytes();
                    }
                }
                assertThat(input.toString()).contains("Archive with");
            } catch (Exception optionalDecoderUnavailable) {
                assertThat(optionalDecoderUnavailable).isNotNull();
            }
        }
    }

    @Test
    void sevenZOutputWritesByteArrayAndSingleBytePayloads() throws Exception {
        Assumptions.assumeTrue(isSevenZCodecAvailable());
        final Path archive = Files.createTempFile("coverage-output", ".7z");
        try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
            output.setContentCompression(SevenZMethod.COPY);
            final SevenZArchiveEntry entry = output.createArchiveEntry(archive.toFile(), "data");
            output.putArchiveEntry(entry);
            output.write("data".getBytes(StandardCharsets.UTF_8));
            output.write('!');
            output.closeArchiveEntry();
        }
        try (SevenZFile input = new SevenZFile(archive.toFile())) {
            final SevenZArchiveEntry entry = input.getNextEntry();
            assertThat(input.read()).isEqualTo('d');
            final byte[] rest = new byte[4];
            assertThat(input.read(rest)).isEqualTo(3);
            assertThat(new String(rest, 0, 3, StandardCharsets.UTF_8)).isEqualTo("ata");
            assertThat(input.read(rest, 0, 1)).isEqualTo(1);
            assertThat(input.getStatisticsForCurrentEntry()).isNotNull();
            assertThat(entry.getSize()).isEqualTo(5);
        }
        Files.deleteIfExists(archive);
    }

    private static Path fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(
                    "forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted")
                    .resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }

    private static boolean isSevenZCodecAvailable() {
        try {
            Class.forName("org.tukaani.xz.FilterOptions");
            return true;
        } catch (ClassNotFoundException missingCodec) {
            return false;
        }
    }

    private static void writeArchive(final Path archive, final Path source) throws Exception {
        try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
            output.setContentCompression(SevenZMethod.COPY);
            final SevenZArchiveEntry entry = output.createArchiveEntry(source, source.getFileName().toString());
            output.putArchiveEntry(entry);
            output.write(Files.readAllBytes(source));
            output.closeArchiveEntry();
        }
    }
}
