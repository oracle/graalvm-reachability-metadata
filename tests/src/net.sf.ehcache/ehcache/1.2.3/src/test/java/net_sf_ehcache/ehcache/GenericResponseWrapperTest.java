/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import net.sf.ehcache.constructs.web.GenericResponseWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class GenericResponseWrapperTest {
    @Test
    void capturesBodyAndResponsePropertiesForCaching() throws Exception {
        WebResponse response = new WebResponse();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        GenericResponseWrapper wrapper = new GenericResponseWrapper(response, captured);
        Cookie cookie = new Cookie("session", "active");
        wrapper.setStatus(HttpServletResponse.SC_CREATED);
        wrapper.setContentType("text/plain");
        wrapper.setContentLength(7);
        wrapper.addHeader("X-Cache", "fresh");
        wrapper.addCookie(cookie);
        wrapper.getWriter().write("created");
        wrapper.flush();

        assertEquals("created", captured.toString());
        assertEquals(0, response.body.size());
        assertEquals(HttpServletResponse.SC_CREATED, wrapper.getStatus());
        assertEquals(wrapper.getStatus(), response.status);
        assertEquals("text/plain", wrapper.getContentType());
        assertEquals("text/plain", response.contentType);
        assertEquals(7, wrapper.getContentLength());
        String[] header = (String[]) wrapper.getHeaders().iterator().next();
        assertEquals("X-Cache", header[0]);
        assertEquals("fresh", header[1]);
        assertSame(cookie, wrapper.getCookies().iterator().next());
        assertSame(cookie, response.cookies.get(0));

        wrapper.reset();
        assertEquals(HttpServletResponse.SC_OK, wrapper.getStatus());
        assertEquals(0, wrapper.getContentLength());
        assertTrue(wrapper.getHeaders().isEmpty());
        assertTrue(wrapper.getCookies().isEmpty());
    }
}
