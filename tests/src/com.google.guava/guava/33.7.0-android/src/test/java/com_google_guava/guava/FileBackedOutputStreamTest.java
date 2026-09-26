/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class FileBackedOutputStreamTest {
    @Test
    void constructorInitializesReachabilityFenceProbeAndByteSourceReadsMemoryBuffer() throws Exception {
        FileBackedOutputStream stream = new FileBackedOutputStream(32);
        ByteSource byteSource = stream.asByteSource();

        stream.write("in-memory data".getBytes(UTF_8));
        stream.flush();

        assertThat(byteSource.asCharSource(UTF_8).read()).isEqualTo("in-memory data");

        stream.reset();
        assertThat(byteSource.read()).isEmpty();
    }

    @Test
    void writesPastThresholdCanBeReadAfterSwitchingToFileBuffer() throws Exception {
        FileBackedOutputStream stream = new FileBackedOutputStream(4);
        ByteSource byteSource = stream.asByteSource();

        stream.write('a');
        stream.write("bcdef".getBytes(UTF_8));
        stream.close();

        try (InputStream input = byteSource.openStream()) {
            assertThat(new String(input.readAllBytes(), UTF_8)).isEqualTo("abcdef");
        } finally {
            stream.reset();
        }
    }
}
