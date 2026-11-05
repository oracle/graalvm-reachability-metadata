/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.DigestScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class DigestSchemeTest {
    @Test
    void computesDigestForKnownChallenge() throws Exception {
        DigestScheme scheme = new DigestScheme();
        scheme.processChallenge("Digest realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"server-token\"");
        String header = scheme.authenticate(new UsernamePasswordCredentials("Mufasa", "Circle Of Life"), new GetMethod("/dir/index.html"));
        assertTrue(header.startsWith("Digest "));
        assertTrue(header.contains("response=\"670fd8c2df070c60b045671b8b24ff02\""));
        assertTrue(header.contains("uri=\"/dir/index.html\""));
        assertTrue(header.contains("opaque=\"server-token\""));
    }
}
