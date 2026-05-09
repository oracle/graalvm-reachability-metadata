/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import org.apache.jasper.xmlparser.UTF8Reader;
import org.junit.jupiter.api.Test;

public class UTF8ReaderTest {
    @Test
    void constructorInitializesLoggerClassAndReadDecodesUtf8Characters() throws Exception {
        final String text = "ASCII \u00a2 \u20ac \uD83D\uDE00";

        try (UTF8Reader reader = newReader(text)) {
            final StringBuilder decoded = new StringBuilder();
            int value = reader.read();
            while (value != -1) {
                decoded.append((char) value);
                value = reader.read();
            }

            assertThat(decoded).hasToString(text);
        }
    }

    @Test
    void readIntoCharArrayHonorsOffsetAndLength() throws Exception {
        try (UTF8Reader reader = newReader("JSP")) {
            final char[] target = new char[] {'x', 'x', 'x', 'x', 'x'};

            final int read = reader.read(target, 1, 3);

            assertThat(read).isEqualTo(3);
            assertThat(target).containsExactly('x', 'J', 'S', 'P', 'x');
        }
    }

    @Test
    void skipConsumesDecodedCharacters() throws Exception {
        try (UTF8Reader reader = newReader("one")) {
            assertThat(reader.skip(2)).isEqualTo(2);
            assertThat(reader.read()).isEqualTo('e');
            assertThat(reader.read()).isEqualTo(-1);
        }
    }

    @Test
    void syntheticClassResolutionMethodResolvesRequestedClass() throws Throwable {
        final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                UTF8Reader.class,
                MethodHandles.lookup());
        final MethodHandle classResolver = lookup.findStatic(
                UTF8Reader.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        final Class<?> resolvedClass = (Class<?>) classResolver.invoke(UTF8ReaderTest.class.getName());

        assertThat(resolvedClass).isSameAs(UTF8ReaderTest.class);
    }

    private static UTF8Reader newReader(String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new UTF8Reader(new ByteArrayInputStream(bytes), UTF8Reader.DEFAULT_BUFFER_SIZE);
    }
}
