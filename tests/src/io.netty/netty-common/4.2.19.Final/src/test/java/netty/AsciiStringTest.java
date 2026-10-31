/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AsciiStringTest {
    @Test
    void usesOffsetHeaderNamesAsMapKeys() {
        byte[] encoded = "!Content-Length?".getBytes(StandardCharsets.US_ASCII);
        AsciiString header = new AsciiString(encoded, 1, encoded.length - 2, false);
        Map<AsciiString, String> headers = new HashMap<>();
        headers.put(header, "128");

        Assertions.assertEquals("128", headers.get(new AsciiString("Content-Length")));
        Assertions.assertTrue(header.contentEqualsIgnoreCase("content-length"));
        Assertions.assertEquals(new AsciiString("content-length").hashCode(), header.hashCode());
        Assertions.assertFalse(header.contentEqualsIgnoreCase("content-type"));
        Assertions.assertEquals("content-length", header.toLowerCase().toString());
        Assertions.assertEquals(128, new AsciiString(headers.get(header)).parseInt());
    }

    @Test
    void invalidatesCachedContentAfterSharedStorageChanges() {
        byte[] encoded = "[original]".getBytes(StandardCharsets.US_ASCII);
        AsciiString shared = new AsciiString(encoded, false).subSequence(1, 9, false);
        AsciiString copied = shared.subSequence(0, 4);
        Assertions.assertEquals("original", shared.toString());
        Assertions.assertEquals(new AsciiString("original").hashCode(), shared.hashCode());

        encoded[1] = 'O';
        shared.arrayChanged();

        Assertions.assertEquals("Original", shared.toString());
        Assertions.assertEquals(new AsciiString("Original"), shared);
        Assertions.assertEquals("orig", copied.toString());
        Assertions.assertEquals('O', shared.charAt(0));
    }
}
