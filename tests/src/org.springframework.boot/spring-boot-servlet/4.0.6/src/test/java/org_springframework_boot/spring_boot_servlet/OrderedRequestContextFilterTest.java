/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;

import org.springframework.boot.servlet.filter.OrderedRequestContextFilter;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderedRequestContextFilterTest {

    @Test
    void servletConfiguredFilterBindsRequestContextAndCompletesItAfterTheChain()
            throws ServletException, IOException {
        OrderedRequestContextFilter filter = new OrderedRequestContextFilter();
        MockFilterConfig config = new MockFilterConfig();
        config.addInitParameter("order", "23");
        filter.init(config);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");
            request.addPreferredLocale(Locale.FRENCH);
            MockHttpServletResponse response = new MockHttpServletResponse();
            List<String> events = new ArrayList<>();
            assertThat(RequestContextHolder.getRequestAttributes()).isNull();
            assertThat(LocaleContextHolder.getLocaleContext()).isNull();

            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                        .currentRequestAttributes();
                assertThat(attributes.getRequest()).isSameAs(servletRequest);
                assertThat(attributes.getResponse()).isSameAs(servletResponse);
                assertThat(LocaleContextHolder.getLocale()).isEqualTo(Locale.FRENCH);
                attributes.setAttribute("orderId", "42", RequestAttributes.SCOPE_REQUEST);
                attributes.registerDestructionCallback("orderId", () -> events.add("completed"),
                        RequestAttributes.SCOPE_REQUEST);
                events.add("handled");
            });

            assertThat(filter.getOrder()).isEqualTo(23);
            assertThat(request.getAttribute("orderId")).isEqualTo("42");
            assertThat(events).containsExactly("handled", "completed");
            assertThat(RequestContextHolder.getRequestAttributes()).isNull();
            assertThat(LocaleContextHolder.getLocaleContext()).isNull();
        } finally {
            filter.destroy();
        }
    }

}
