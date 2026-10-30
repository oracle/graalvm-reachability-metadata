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
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class StringPartTest {
    @Test
    void writesTextPartUsingSelectedCharset() throws Exception {
        StringPart part = new StringPart("message", "caf\u00e9", "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        part.send(output);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("caf\u00e9"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("name=\"message\""));
        assertEquals(output.size(), part.length());
        assertTrue(part.isRepeatable());
    }
}
