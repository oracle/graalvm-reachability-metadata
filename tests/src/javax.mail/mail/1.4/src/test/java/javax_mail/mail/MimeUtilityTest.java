/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.internet.MimeUtility;
import org.junit.jupiter.api.Test;

public class MimeUtilityTest {
    @Test
    void mapsCharsetsAndRoundTripsEncodedHeaders() throws Exception {
        assertEquals("ISO-8859-1", MimeUtility.mimeCharset("8859_1"));
        assertEquals("UTF8", MimeUtility.javaCharset("utf-8"));
        String subject = "Gr\u00fc\u00dfe from Andr\u00e9";
        String encoded = MimeUtility.encodeText(subject, "UTF-8", "B");
        assertTrue(encoded.startsWith("=?UTF-8?B?"));
        assertEquals(subject, MimeUtility.decodeText(encoded));
    }

    @Test
    void roundTripsMimeTransferEncodings() throws Exception {
        byte[] content = "Payload with caf\u00e9\r\n".getBytes(StandardCharsets.UTF_8);
        for (String encoding : new String[] {"base64", "quoted-printable"}) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (OutputStream encoder = MimeUtility.encode(output, encoding)) {
                encoder.write(content);
            }
            try (InputStream decoder = MimeUtility.decode(new ByteArrayInputStream(output.toByteArray()), encoding)) {
                assertEquals(new String(content, StandardCharsets.UTF_8), new String(decoder.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
