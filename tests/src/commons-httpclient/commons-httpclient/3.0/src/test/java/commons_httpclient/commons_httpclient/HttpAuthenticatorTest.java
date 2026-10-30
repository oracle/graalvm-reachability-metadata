/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.HttpAuthenticator;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpAuthenticatorTest {
    @Test
    void selectsChallengeAndProducesBasicCredentials() throws Exception {
        AuthScheme scheme = HttpAuthenticator.selectAuthScheme(new Header[] {new Header("WWW-Authenticate", "Basic realm=\"private\"")});
        assertEquals("private", scheme.getRealm());
        assertEquals("Basic dXNlcjpwYXNz", scheme.authenticate(new UsernamePasswordCredentials("user", "pass"), new GetMethod("/")));
    }
}
