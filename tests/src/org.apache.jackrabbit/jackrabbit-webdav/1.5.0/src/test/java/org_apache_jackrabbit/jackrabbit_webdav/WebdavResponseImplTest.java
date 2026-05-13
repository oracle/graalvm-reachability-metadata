/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.jackrabbit.webdav.WebdavResponseImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

public class WebdavResponseImplTest {
    @Test
    public void constructorWithNoCacheAddsCacheControlHeaders() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        WebdavResponseImpl response = new WebdavResponseImpl(servletResponse, true);

        assertThat(response.containsHeader("Pragma")).isTrue();
        assertThat(response.containsHeader("Cache-Control")).isTrue();
        assertThat(servletResponse.getHeader("Pragma")).isEqualTo("No-cache");
        assertThat(servletResponse.getHeader("Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    public void sendErrorDelegatesStatusAndMessageToServletResponse() throws IOException {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        WebdavResponseImpl response = new WebdavResponseImpl(servletResponse);

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");

        assertThat(servletResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(servletResponse.getErrorMessage()).isEqualTo("Access denied");
    }
}
