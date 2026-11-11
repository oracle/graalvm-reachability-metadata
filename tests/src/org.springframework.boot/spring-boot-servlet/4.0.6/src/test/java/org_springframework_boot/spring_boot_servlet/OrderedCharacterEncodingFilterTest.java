/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.boot.servlet.filter.OrderedCharacterEncodingFilter;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderedCharacterEncodingFilterTest {

    @Test
    void servletConfiguredFilterForcesEncodingBeforeTheApplicationHandlesTheRequest()
            throws ServletException, IOException {
        OrderedCharacterEncodingFilter filter = new OrderedCharacterEncodingFilter();
        MockFilterConfig config = new MockFilterConfig();
        config.addInitParameter("encoding", StandardCharsets.UTF_8.name());
        config.addInitParameter("forceRequestEncoding", "true");
        config.addInitParameter("forceResponseEncoding", "true");
        config.addInitParameter("order", "17");
        filter.init(config);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/messages");
            request.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
            List<String> observedEncodings = new ArrayList<>();

            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                observedEncodings.add(servletRequest.getCharacterEncoding());
                observedEncodings.add(servletResponse.getCharacterEncoding());
                HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
                httpResponse.getWriter().write("caf\u00e9");
            });

            assertThat(filter.getOrder()).isEqualTo(17);
            assertThat(observedEncodings).containsExactly(StandardCharsets.UTF_8.name(), StandardCharsets.UTF_8.name());
            assertThat(response.getContentAsByteArray()).containsExactly("caf\u00e9".getBytes(StandardCharsets.UTF_8));
            assertThat(response.getContentAsString()).isEqualTo("caf\u00e9");
        } finally {
            filter.destroy();
        }
    }

}
