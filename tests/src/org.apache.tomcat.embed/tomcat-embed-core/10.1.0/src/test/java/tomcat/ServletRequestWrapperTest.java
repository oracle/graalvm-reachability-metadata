/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.ServletRequestWrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServletRequestWrapperTest {
    @Test
    void delegatesRequestAttributesAndRejectsNullReplacement() {
        Request request = new Request(new Connector());
        ServletRequestWrapper wrapper = new ServletRequestWrapper(request);
        wrapper.setAttribute("trace", "request-1");
        assertEquals("request-1", request.getAttribute("trace"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> wrapper.setRequest(null));
        assertFalse(error.getMessage().isBlank());
        assertEquals("request-1", wrapper.getAttribute("trace"));
    }
}
