/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class AuthPolicyTest {
    @Test
    void createsBuiltInSchemesAndProducesAuthorization() throws Exception {
        GetMethod method = new GetMethod("/private");
        AuthScheme basic = AuthPolicy.getAuthScheme("BASIC");
        basic.processChallenge("Basic realm=\"private\"");
        assertEquals("Basic dXNlcjpwYXNz", basic.authenticate(new UsernamePasswordCredentials("user", "pass"), method));
        AuthScheme digest = AuthPolicy.getAuthScheme(AuthPolicy.DIGEST);
        digest.processChallenge("Digest realm=\"private\", nonce=\"server-nonce\"");
        String authorization = digest.authenticate(new UsernamePasswordCredentials("user", "pass"), method);
        assertTrue(authorization.startsWith("Digest "));
        assertTrue(authorization.contains("uri=\"/private\""));
        AuthScheme ntlm = AuthPolicy.getAuthScheme(AuthPolicy.NTLM);
        ntlm.processChallenge("NTLM");
        assertTrue(ntlm.authenticate(new NTCredentials("user", "pass", "WORKSTATION", "DOMAIN"), method).startsWith("NTLM TlRMTVNTUAAB"));
    }
}
