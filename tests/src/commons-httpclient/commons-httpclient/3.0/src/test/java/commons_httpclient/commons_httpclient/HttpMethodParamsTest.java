/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpMethodParamsTest {
    @Test
    void configuresRequestEncodingProtocolAndCookiePolicy() {
        HttpMethodParams params = new HttpMethodParams();
        params.setContentCharset("UTF-8");
        params.setCredentialCharset("UTF-8");
        params.setVersion(HttpVersion.HTTP_1_0);
        params.setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
        assertEquals("UTF-8", params.getContentCharset());
        assertEquals("UTF-8", params.getCredentialCharset());
        assertEquals(HttpVersion.HTTP_1_0, params.getVersion());
        assertEquals(CookiePolicy.BROWSER_COMPATIBILITY, params.getCookiePolicy());
    }
}
