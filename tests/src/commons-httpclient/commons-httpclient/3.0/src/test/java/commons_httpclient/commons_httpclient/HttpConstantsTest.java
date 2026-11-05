/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.HttpConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpConstantsTest {
    @Test
    void encodesProtocolElementsAndContent() {
        assertArrayEquals("Accept: text/plain".getBytes(StandardCharsets.US_ASCII), HttpConstants.getBytes("Accept: text/plain"));
        assertEquals("text/plain", HttpConstants.getString(HttpConstants.getBytes("text/plain")));
        String text = "caf\u00e9";
        assertEquals(text, HttpConstants.getContentString(HttpConstants.getContentBytes(text, "UTF-8"), "UTF-8"));
    }
}
