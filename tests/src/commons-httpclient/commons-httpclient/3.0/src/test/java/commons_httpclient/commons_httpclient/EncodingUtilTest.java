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
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class EncodingUtilTest {
    @Test
    void encodesFormValuesAndText() {
        NameValuePair[] values = {new NameValuePair("query", "hello world"), new NameValuePair("symbol", "a+b")};
        assertEquals("query=hello+world&symbol=a%2Bb", EncodingUtil.formUrlEncode(values, "UTF-8"));
        String text = "caf\u00e9";
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), EncodingUtil.getBytes(text, "UTF-8"));
        assertEquals(text, EncodingUtil.getString(EncodingUtil.getBytes(text, "UTF-8"), "UTF-8"));
        assertEquals("header", EncodingUtil.getAsciiString(EncodingUtil.getAsciiBytes("header")));
    }
}
