/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tukaani_xz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.SeekableFileInputStream;
import org.tukaani.xz.SeekableXZInputStream;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's seekable archive indexing subsystem for random-access event retrieval.
 */
public class SeekableEventArchiveTest {
    @TempDir
    Path archiveDirectory;

    @Test
    void retrievesEventsByOffsetFromSeekableArchive() throws IOException {
        List<byte[]> events = List.of(
                "2026-01-15T12:00:00Z order=1001 status=packed".getBytes(StandardCharsets.UTF_8),
                "2026-01-15T12:01:00Z order=1002 status=shipped".getBytes(StandardCharsets.UTF_8),
                "2026-01-15T12:02:00Z order=1003 status=delivered".getBytes(StandardCharsets.UTF_8),
                "2026-01-15T12:03:00Z order=1004 status=returned".getBytes(StandardCharsets.UTF_8));
        Path archivePath = archiveDirectory.resolve("events.xz");
        LZMA2Options options = new LZMA2Options(1);
        options.setDictSize(1 << 16);

        try (OutputStream file = Files.newOutputStream(archivePath);
                XZOutputStream archive = new XZOutputStream(file, options, XZ.CHECK_CRC64)) {
            for (byte[] event : events) {
                archive.write(event);
                archive.endBlock();
            }
        }

        try (SeekableFileInputStream file = new SeekableFileInputStream(archivePath.toFile());
                SeekableXZInputStream archive = new SeekableXZInputStream(file)) {
            assertThat(archive.getStreamCount()).isEqualTo(1);
            assertThat(archive.getBlockCount()).isEqualTo(events.size());

            for (int eventIndex : new int[] {2, 0, 3, 1}) {
                byte[] expected = events.get(eventIndex);
                long position = archive.getBlockPos(eventIndex);

                assertThat(archive.getBlockNumber(position)).isEqualTo(eventIndex);
                assertThat(archive.getBlockSize(eventIndex)).isEqualTo(expected.length);
                archive.seek(position);
                assertThat(archive.readNBytes(expected.length)).containsExactly(expected);
            }
        }
    }
}
