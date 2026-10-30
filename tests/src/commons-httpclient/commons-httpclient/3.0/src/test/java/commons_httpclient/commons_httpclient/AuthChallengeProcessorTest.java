/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.auth.AuthChallengeProcessor;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthState;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class AuthChallengeProcessorTest {
    @Test
    void selectsPreferredSchemeAndUpdatesAuthenticationState() throws Exception {
        HttpClientParams params = new HttpClientParams();
        params.setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, List.of(AuthPolicy.BASIC, AuthPolicy.DIGEST));
        AuthChallengeProcessor processor = new AuthChallengeProcessor(params);
        AuthState state = new AuthState();
        AuthScheme scheme = processor.processChallenge(state, Map.of("basic", "Basic realm=\"private\"",
                "digest", "Digest realm=\"private\", nonce=\"nonce\""));
        assertEquals("basic", scheme.getSchemeName());
        assertEquals("private", scheme.getRealm());
        assertSame(scheme, state.getAuthScheme());
    }
}
