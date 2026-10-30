/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class PartTest {
    @Test
    void combinesPartsWithExplicitMultipartBoundary() throws Exception {
        Part[] parts = {new StringPart("first", "alpha"), new StringPart("second", "beta")};
        byte[] boundary = "consumer-boundary".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Part.sendParts(output, parts, boundary);
        String wire = output.toString(StandardCharsets.US_ASCII);
        assertTrue(wire.startsWith("--consumer-boundary\r\n"));
        assertTrue(wire.endsWith("--consumer-boundary--\r\n"));
        assertTrue(wire.contains("alpha"));
        assertTrue(wire.contains("beta"));
        assertEquals(output.size(), Part.getLengthOfParts(parts, boundary));
    }
}
