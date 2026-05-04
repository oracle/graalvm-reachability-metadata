/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox_io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadMemoryMappedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IOUtilsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesTheCompleteInputStream() throws IOException {
        byte[] expected = "PDFBox IOUtils copy payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long copiedBytes = IOUtils.copy(new ByteArrayInputStream(expected), output);

        assertThat(copiedBytes).isEqualTo(expected.length);
        assertThat(output.toByteArray()).isEqualTo(expected);
        assertThat(IOUtils.toByteArray(new ByteArrayInputStream(expected))).isEqualTo(expected);
    }

    @Test
    void unmapsDirectBuffersWithoutExposingPlatformCleanerDetails() {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(32);
        directBuffer.putInt(0, 0x50444632);

        assertThatCode(() -> IOUtils.unmap(directBuffer)).doesNotThrowAnyException();
        assertThatCode(() -> IOUtils.unmap(null)).doesNotThrowAnyException();
    }

    @Test
    void closesMemoryMappedReaderAndUnmapsItsBuffer() throws IOException {
        byte[] content = "memory mapped pdfbox io".getBytes(StandardCharsets.UTF_8);
        Path mappedFile = temporaryDirectory.resolve("mapped-input.bin");
        Files.write(mappedFile, content);

        try (RandomAccessReadMemoryMappedFile reader = new RandomAccessReadMemoryMappedFile(mappedFile)) {
            byte[] actual = new byte[content.length];

            assertThat(reader.length()).isEqualTo(content.length);
            assertThat(reader.read(actual, 0, actual.length)).isEqualTo(content.length);
            assertThat(actual).isEqualTo(content);
        }

        assertThatCode(() -> Files.delete(mappedFile)).doesNotThrowAnyException();
    }
}
