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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServletResponseWrapperTest {
    @Test
    void delegatesResponsePropertiesAndRejectsNullResponses() {
        var response = new Connector().createResponse();
        response.setCoyoteResponse(new Response());
        ServletResponseWrapper wrapper = new ServletResponseWrapper(response);
        wrapper.setCharacterEncoding("UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThatThrownBy(() -> wrapper.setResponse(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> new ServletResponseWrapper(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
