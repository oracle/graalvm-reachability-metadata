/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.FormContentFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class FormContentFilterTest {

    @Test
    void decodesPutFormAndPreservesParametersDuringNestedDispatch() throws Exception {
        FormContentFilter filter = new FormContentFilter();
        filter.init(new MockFilterConfig("forms"));
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/items");
            request.setContentType("application/x-www-form-urlencoded;charset=UTF-8");
            request.setQueryString("name=query");
            request.addParameter("name", "query");
            request.setContent("name=body&label=caf%C3%A9".getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicInteger calls = new AtomicInteger();
            FilterChain terminal = (incoming, outgoing) -> {
                HttpServletRequest form = (HttpServletRequest) incoming;
                assertThat(form.getParameterValues("name")).containsExactly("query", "body");
                assertThat(form.getParameter("label")).isEqualTo("caf\u00e9");
                calls.incrementAndGet();
            };
            filter.doFilter(request, response, (incoming, outgoing) ->
                    filter.doFilter(incoming, outgoing, terminal));
            assertThat(calls.get()).isEqualTo(1);
            assertThat(request.getAttribute("forms.FILTERED")).isNull();
        } finally {
            filter.destroy();
        }
    }
}
