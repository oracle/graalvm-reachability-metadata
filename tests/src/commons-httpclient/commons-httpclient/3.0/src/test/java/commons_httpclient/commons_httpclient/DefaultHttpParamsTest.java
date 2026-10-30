/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.commons.httpclient.params.DefaultHttpParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class DefaultHttpParamsTest {
    @Test
    void resolvesDefaultsAndLocalOverrides() {
        DefaultHttpParams defaults = new DefaultHttpParams(null);
        defaults.setParameter("application.mode", "default");
        DefaultHttpParams params = new DefaultHttpParams(defaults);
        assertEquals("default", params.getParameter("application.mode"));
        assertFalse(params.isParameterSetLocally("application.mode"));
        params.setParameter("application.mode", "override");
        params.setIntParameter("application.limit", 7);
        assertEquals("override", params.getParameter("application.mode"));
        assertEquals(7, params.getIntParameter("application.limit", 0));
        params.clear();
        assertEquals("default", params.getParameter("application.mode"));
    }
}
