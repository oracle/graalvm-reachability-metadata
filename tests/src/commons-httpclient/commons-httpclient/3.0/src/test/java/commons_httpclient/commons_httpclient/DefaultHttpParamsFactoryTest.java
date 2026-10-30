/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.params.DefaultHttpParamsFactory;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.params.HttpParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class DefaultHttpParamsFactoryTest {
    @Test
    void suppliesCachedHttpProtocolDefaults() {
        DefaultHttpParamsFactory factory = new DefaultHttpParamsFactory();
        HttpParams params = factory.getDefaultParams();
        assertSame(params, factory.getDefaultParams());
        assertEquals(HttpVersion.HTTP_1_1, params.getParameter(HttpMethodParams.PROTOCOL_VERSION));
        assertEquals("US-ASCII", params.getParameter(HttpMethodParams.HTTP_ELEMENT_CHARSET));
        assertEquals("ISO-8859-1", params.getParameter(HttpMethodParams.HTTP_CONTENT_CHARSET));
    }
}
