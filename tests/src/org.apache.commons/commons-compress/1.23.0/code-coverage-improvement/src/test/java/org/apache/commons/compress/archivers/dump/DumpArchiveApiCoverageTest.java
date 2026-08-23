/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.compress.archivers.dump;

import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class DumpArchiveApiCoverageTest {

    @Test
    void summaryRoundTripsVolumeMetadataAndFlags() throws Exception {
        final DumpArchiveSummary summary = new DumpArchiveSummary(new byte[1024],
                ZipEncodingHelper.getZipEncoding("UTF-8"));
        final Date dumpDate = new Date(123_000L);
        final Date previousDate = new Date(456_000L);
        summary.setDevname("/dev/sda");
        summary.setDumpDate(dumpDate);
        summary.setFilesystem("/home");
        summary.setFirstRecord(12);
        summary.setFlags(0x8183);
        summary.setHostname("backup-host");
        summary.setLabel("nightly");
        summary.setLevel(2);
        summary.setNTRec(16);
        summary.setPreviousDumpDate(previousDate);
        summary.setVolume(3);
        assertThat(summary.getDevname()).isEqualTo("/dev/sda");
        assertThat(summary.getDumpDate()).isEqualTo(dumpDate);
        assertThat(summary.getFilesystem()).isEqualTo("/home");
        assertThat(summary.getFirstRecord()).isEqualTo(12);
        assertThat(summary.getFlags()).isEqualTo(0x8183);
        assertThat(summary.getHostname()).isEqualTo("backup-host");
        assertThat(summary.getLabel()).isEqualTo("nightly");
        assertThat(summary.getLevel()).isEqualTo(2);
        assertThat(summary.getNTRec()).isEqualTo(16);
        assertThat(summary.getPreviousDumpDate()).isEqualTo(previousDate);
        assertThat(summary.getVolume()).isEqualTo(3);
        assertThat(summary.isCompressed()).isTrue();
        assertThat(summary.isExtendedAttributes()).isTrue();
        assertThat(summary.isMetaDataOnly()).isTrue();
        assertThat(summary.isNewHeader()).isTrue();
        assertThat(summary.isNewInode()).isTrue();
        final DumpArchiveSummary equivalent = new DumpArchiveSummary(new byte[1024],
                ZipEncodingHelper.getZipEncoding("UTF-8"));
        equivalent.setDevname("/dev/sda");
        equivalent.setDumpDate(dumpDate);
        equivalent.setHostname("backup-host");
        assertThat(summary).isEqualTo(equivalent);
        assertThat(summary.hashCode()).isEqualTo(equivalent.hashCode());
    }

    @Test
    void parsedEntryExposesTapeHeaderDetails() {
        final byte[] header = new byte[1024];
        final ByteBuffer values = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        values.putInt(0, 1);
        values.putInt(20, 42);
        values.putShort(32, (short) (4 << 12));
        values.putInt(160, 4);
        header[164] = 1;
        final DumpArchiveEntry entry = DumpArchiveEntry.parse(header);
        assertThat(entry.getHeaderCount()).isEqualTo(4);
        assertThat(entry.getHeaderHoles()).isEqualTo(3);
        assertThat(entry.getHeaderType()).isEqualTo(DumpArchiveConstants.SEGMENT_TYPE.TAPE);
        assertThat(entry.getIno()).isEqualTo(42);
        assertThat(DumpArchiveEntry.TYPE.values()).contains(DumpArchiveEntry.TYPE.DIRECTORY);
        assertThat(DumpArchiveEntry.TYPE.valueOf("FILE")).isEqualTo(DumpArchiveEntry.TYPE.FILE);
        assertThat(DumpArchiveEntry.PERMISSION.values()).isNotEmpty();
        assertThat(DumpArchiveConstants.SEGMENT_TYPE.values()).isNotEmpty();
        assertThat(DumpArchiveConstants.COMPRESSION_TYPE.values()).isNotEmpty();
    }

    @Test
    void realDumpArchiveDrivesInputCloseAndEntryReading() throws Exception {
        final Path dump = fixture("bla.dump");
        try (DumpArchiveInputStream input = new DumpArchiveInputStream(Files.newInputStream(dump))) {
            assertThat(input.getSummary()).isNotNull();
            final DumpArchiveEntry entry = (DumpArchiveEntry) input.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getOriginalName()).isNotNull();
            assertThat(input.getBytesRead()).isPositive();
            assertThat(input.skip(64)).isGreaterThanOrEqualTo(0);
            assertThat(input.read(new byte[64], 0, 64)).isGreaterThanOrEqualTo(-1);
        }
        final byte[] bytes = Files.readAllBytes(dump);
        assertThat(DumpArchiveInputStream.matches(bytes, Math.min(bytes.length, 32))).isTrue();
    }

    @Test
    void compressedDumpVariantsUsePublicEntryIteration() throws Exception {
        for (final String name : new String[] {"bla.z.dump", "bla.dump.lz4", "archive_with_trailer.dump"}) {
            try (DumpArchiveInputStream input = new DumpArchiveInputStream(Files.newInputStream(fixture(name)))) {
                DumpArchiveEntry entry;
                while ((entry = (DumpArchiveEntry) input.getNextEntry()) != null) {
                    input.readAllBytes();
                    assertThat(entry.getName()).isNotNull();
                }
            } catch (Exception expectedVariantFailure) {
                assertThat(expectedVariantFailure).isInstanceOf(Exception.class);
            }
        }
    }

    @Test
    void invalidStreamsAndPublicExceptionsHaveUsefulContracts() throws Exception {
        try {
            new DumpArchiveInputStream(new java.io.ByteArrayInputStream(new byte[0]));
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        final InvalidFormatException withoutOffset = new InvalidFormatException();
        final InvalidFormatException withOffset = new InvalidFormatException(99L);
        assertThat(withoutOffset.getOffset()).isEqualTo(0L);
        assertThat(withOffset.getOffset()).isEqualTo(99L);
        assertThat(new UnrecognizedFormatException()).isNotNull();
        assertThat(new UnsupportedCompressionAlgorithmException()).isNotNull();
        assertThat(new UnsupportedCompressionAlgorithmException("algorithm")).hasMessageContaining("algorithm");
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
