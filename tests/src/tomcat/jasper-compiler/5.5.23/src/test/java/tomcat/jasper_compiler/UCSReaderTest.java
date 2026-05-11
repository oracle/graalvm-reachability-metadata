/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;

import org.apache.jasper.xmlparser.UCSReader;
import org.junit.jupiter.api.Test;

public class UCSReaderTest {
    @Test
    void readsSingleCharactersFromAllSupportedUcsEncodings() throws Exception {
        assertThat(readSingle(new byte[] {0x00, 0x41}, UCSReader.UCS2BE)).isEqualTo('A');
        assertThat(readSingle(new byte[] {0x41, 0x00}, UCSReader.UCS2LE)).isEqualTo('A');
        assertThat(readSingle(
                new byte[] {0x00, 0x00, 0x00, 0x41},
                UCSReader.UCS4BE)).isEqualTo('A');
        assertThat(readSingle(
                new byte[] {0x41, 0x00, 0x00, 0x00},
                UCSReader.UCS4LE)).isEqualTo('A');
    }

    @Test
    void readsBufferedCharactersWithOffsetAndClosesUnderlyingStream() throws Exception {
        clearSyntheticClassCache();
        final CloseRecordingInputStream input = new CloseRecordingInputStream(new byte[] {
                0x00, 0x41,
                0x00, 0x42,
                0x00, 0x43,
        });
        final UCSReader reader = new UCSReader(input, 4, UCSReader.UCS2BE);
        final char[] target = new char[] {'x', 'x', 'x', 'x'};

        final int read = reader.read(target, 1, 2);
        reader.close();

        assertThat(read).isEqualTo(2);
        assertThat(target).containsExactly('x', 'A', 'B', 'x');
        assertThat(input.closed).isTrue();
    }

    @Test
    void delegatesMarkResetSkipAndMarkSupportToUnderlyingStream() throws Exception {
        clearSyntheticClassCache();
        final byte[] bytes = new byte[] {0x00, 0x41, 0x00, 0x42, 0x00, 0x43};
        final UCSReader reader = new UCSReader(new ByteArrayInputStream(bytes), UCSReader.UCS2BE);

        assertThat(reader.markSupported()).isTrue();
        assertThat(reader.ready()).isFalse();

        reader.mark(bytes.length);
        assertThat(reader.read()).isEqualTo('A');
        assertThat(reader.skip(1)).isEqualTo(1);
        assertThat(reader.read()).isEqualTo('C');

        reader.reset();
        assertThat(reader.read()).isEqualTo('A');
        reader.close();
    }

    @Test
    void constructorResolvesLoggerClassWhenSyntheticClassCacheIsEmpty() throws Exception {
        clearSyntheticClassCache();

        try (UCSReader reader = new UCSReader(
                new ByteArrayInputStream(new byte[] {0x00, 0x5a}),
                UCSReader.UCS2BE)) {
            assertThat(reader.read()).isEqualTo('Z');
        }
    }

    private static int readSingle(byte[] bytes, short encoding) throws Exception {
        clearSyntheticClassCache();
        try (UCSReader reader = new UCSReader(new ByteArrayInputStream(bytes), encoding)) {
            return reader.read();
        }
    }

    private static void clearSyntheticClassCache() throws Exception {
        final Field syntheticClassCache = UCSReader.class.getDeclaredField(
                "class$org$apache$jasper$xmlparser$UCSReader");
        syntheticClassCache.setAccessible(true);
        syntheticClassCache.set(null, null);
    }

    private static final class CloseRecordingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseRecordingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
