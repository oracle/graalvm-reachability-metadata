/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class Commons_httpclientTest {
    @Test
    void configuresSecureHostFromUri() throws Exception {
        HostConfiguration host = new HostConfiguration();
        host.setHost(new URI("https://example.test:8443/resource", false));
        assertEquals("example.test", host.getHost());
        assertEquals(8443, host.getPort());
        assertTrue(host.getProtocol().isSecure());
    }
}
