/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.ServletResponseWrapper;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServletResponseWrapperTest {
    @Test
    void delegatesContentLengthAndRejectsNullReplacement() {
        var response = new Connector().createResponse();
        response.setCoyoteResponse(new Response());
        ServletResponseWrapper wrapper = new ServletResponseWrapper(response);
        wrapper.setContentLength(42);
        assertEquals(42, response.getContentLength());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> wrapper.setResponse(null));
        assertFalse(error.getMessage().isBlank());
    }
}
