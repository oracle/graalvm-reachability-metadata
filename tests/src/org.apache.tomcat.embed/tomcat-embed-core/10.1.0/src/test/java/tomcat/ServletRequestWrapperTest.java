/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.ServletRequestWrapper;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.Request;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServletRequestWrapperTest {
    @Test
    void delegatesAttributesAndRejectsNullRequests() {
        var request = new Connector().createRequest();
        request.setCoyoteRequest(new Request());
        ServletRequestWrapper wrapper = new ServletRequestWrapper(request);
        wrapper.setAttribute("trace", "sample");
        assertThat(request.getAttribute("trace")).isEqualTo("sample");
        assertThatThrownBy(() -> wrapper.setRequest(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> new ServletRequestWrapper(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
