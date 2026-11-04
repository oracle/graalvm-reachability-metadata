/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tukaani.xz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/// Exercises XZ stream compression to archive application event records and restore them intact.
public class CompressedEventArchiveTest {
    @Test
    void archivesAndRestoresEventRecordsThroughAnXzStream() throws IOException {
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 240; i++) {
            events.add("2026-01-15T12:00:00Z order=" + (1000 + i % 12)
                    + " status=packed warehouse=north");
        }

        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        LZMA2Options options = new LZMA2Options(1);
        options.setDictSize(1 << 16);
        try (XZOutputStream output = new XZOutputStream(archive, options, XZ.CHECK_CRC64)) {
            for (String event : events) {
                output.write(event.getBytes(StandardCharsets.UTF_8));
                output.write('\n');
            }
        }

        byte[] compressedArchive = archive.toByteArray();
        assertThat(compressedArchive).startsWith(XZ.HEADER_MAGIC);
        assertThat(compressedArchive.length).isLessThan(events.size() * 72);

        List<String> restoredEvents = new ArrayList<>();
        try (XZInputStream input = new XZInputStream(new ByteArrayInputStream(compressedArchive))) {
            StringBuilder currentEvent = new StringBuilder();
            int nextByte;
            while ((nextByte = input.read()) != -1) {
                if (nextByte == '\n') {
                    restoredEvents.add(currentEvent.toString());
                    currentEvent.setLength(0);
                } else {
                    currentEvent.append((char) nextByte);
                }
            }
        }

        assertThat(restoredEvents).containsExactlyElementsOf(events);
    }
}
