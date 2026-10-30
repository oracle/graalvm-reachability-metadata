/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpStateTest {
    @Test
    void replacesCookiesAndMatchesCredentialsByScope() {
        HttpState state = new HttpState();
        state.addCookie(new Cookie("example.test", "session", "old", "/", null, false));
        state.addCookie(new Cookie("example.test", "session", "new", "/", null, false));
        assertEquals(1, state.getCookies().length);
        assertEquals("new", state.getCookies()[0].getValue());
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user", "pass");
        state.setCredentials(new AuthScope("example.test", 80, "private"), credentials);
        assertSame(credentials, state.getCredentials(new AuthScope("example.test", 80, "private")));
        assertNull(state.getCredentials(new AuthScope("elsewhere.test", 80, "private")));
        state.clear();
        assertEquals(0, state.getCookies().length);
        assertNull(state.getCredentials(AuthScope.ANY));
    }
}
