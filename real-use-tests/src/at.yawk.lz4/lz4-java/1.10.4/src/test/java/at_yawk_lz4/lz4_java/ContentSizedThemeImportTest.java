/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE.SIZE_64KB;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.CONTENT_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises LZ4 frame content-size metadata for memory-budgeted tenant theme imports.
/// The importer admits stylesheets by decoded size, not by their much smaller upload size.
public class ContentSizedThemeImportTest {
    @Test
    @Timeout(60)
    public void admitsSizedThemeAndRejectsOversizedOrUnspecifiedUploads() throws IOException {
        String stylesheet = ".warehouse { color: #123456; background: #ffffff; }\n".repeat(200);
        byte[] source = stylesheet.getBytes(UTF_8);
        ThemeImporter importer = new ThemeImporter(source.length);

        byte[] upload = publish(source, true);
        assertEquals(stylesheet, importer.importStylesheet(upload),
                "A theme exactly at the decoded-size limit must retain every CSS rule");

        byte[] oversized = publish(stylesheet.repeat(10).getBytes(UTF_8), true);
        assertTrue(oversized.length < source.length,
                "Compressed upload length alone would incorrectly admit this oversized theme");
        IOException tooLarge = assertThrows(IOException.class, () -> importer.importStylesheet(oversized));
        assertEquals("Theme exceeds decoded-size budget", tooLarge.getMessage());

        byte[] unspecified = publish(source, false);
        IOException missingSize = assertThrows(IOException.class, () -> importer.importStylesheet(unspecified));
        assertEquals("Theme must declare its decoded size", missingSize.getMessage());

        assertEquals(stylesheet, importer.importStylesheet(upload),
                "Rejected uploads must not prevent the next valid theme from being imported");
    }

    private static byte[] publish(byte[] stylesheet, boolean includeSize) throws IOException {
        ByteArrayOutputStream upload = new ByteArrayOutputStream();
        Bits[] features = includeSize
                ? new Bits[]{BLOCK_INDEPENDENCE, CONTENT_CHECKSUM, CONTENT_SIZE}
                : new Bits[]{BLOCK_INDEPENDENCE, CONTENT_CHECKSUM};
        try (LZ4FrameOutputStream output = new LZ4FrameOutputStream(
                upload, SIZE_64KB, stylesheet.length, features)) {
            output.write(stylesheet);
        }
        return upload.toByteArray();
    }

    private static final class ThemeImporter {
        private final int maxDecodedBytes;

        private ThemeImporter(int maxDecodedBytes) {
            this.maxDecodedBytes = maxDecodedBytes;
        }

        private String importStylesheet(byte[] upload) throws IOException {
            // A theme upload is one frame; content-size queries require single-frame mode.
            try (LZ4FrameInputStream input = new LZ4FrameInputStream(new ByteArrayInputStream(upload), true)) {
                if (!input.isExpectedContentSizeDefined()) {
                    throw new IOException("Theme must declare its decoded size");
                }
                long decodedSize = input.getExpectedContentSize();
                if (decodedSize > maxDecodedBytes) {
                    throw new IOException("Theme exceeds decoded-size budget");
                }
                byte[] stylesheet = input.readNBytes(Math.toIntExact(decodedSize));
                // Consume the frame terminator and checksum before accepting the theme.
                if (stylesheet.length != decodedSize || input.read() != -1) {
                    throw new IOException("Theme does not match its declared size");
                }
                return new String(stylesheet, UTF_8);
            }
        }
    }
}
