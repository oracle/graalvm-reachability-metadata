/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.BasicScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class BasicSchemeTest {
    @Test
    void answersBasicChallenge() throws Exception {
        BasicScheme scheme = new BasicScheme();
        scheme.processChallenge("Basic realm=\"private\"");
        assertEquals("private", scheme.getRealm());
        assertTrue(scheme.isComplete());
        assertFalse(scheme.isConnectionBased());
        assertEquals("Basic dXNlcjpwYXNz", scheme.authenticate(new UsernamePasswordCredentials("user", "pass"), new GetMethod("/private")));
    }
}
