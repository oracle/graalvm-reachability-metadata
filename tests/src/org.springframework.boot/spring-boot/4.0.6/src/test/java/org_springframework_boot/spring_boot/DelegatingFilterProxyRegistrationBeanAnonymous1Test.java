/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatingFilterProxyRegistrationBeanAnonymous1Test {
    @Test
    void bindsAProxyRegistrationAndDispatchesToItsLazyFilterBean() throws Exception {
        AtomicInteger created = new AtomicInteger();
        MockServletContext servletContext = new MockServletContext();
        try (GenericWebApplicationContext context = new GenericWebApplicationContext(servletContext)) {
            context.registerBean("ordersFilter", Filter.class, () -> {
                created.incrementAndGet();
                return (request, response, chain) -> {
                    request.setAttribute("filtered", "orders");
                    chain.doFilter(request, response);
                };
            }, definition -> definition.setLazyInit(true));
            context.refresh();
            DelegatingFilterProxyRegistrationBean registration = new DelegatingFilterProxyRegistrationBean("ordersFilter");
            registration.setApplicationContext(context);
            Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                    "filter.name", "orders-security", "filter.order", "3", "filter.match-after", "true",
                    "filter.url-patterns[0]", "/orders/*", "filter.async-supported", "false",
                    "proxy.target-filter-lifecycle", "true")));
            binder.bind("filter", Bindable.ofInstance(registration)).get();
            DelegatingFilterProxy proxy = registration.getFilter();
            binder.bind("proxy", Bindable.ofInstance(proxy)).get();
            proxy.init(new MockFilterConfig(servletContext, "orders-security"));
            try {
                assertThat(created.get()).isZero();
                assertThat(registration.getFilterName()).isEqualTo("orders-security");
                assertThat(registration.getOrder()).isEqualTo(3);
                assertThat(registration.isMatchAfter()).isTrue();
                assertThat(registration.isAsyncSupported()).isFalse();
                assertThat(registration.getUrlPatterns()).containsExactly("/orders/*");
                MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
                MockHttpServletResponse response = new MockHttpServletResponse();

                proxy.doFilter(request, response, (filteredRequest, filteredResponse) ->
                        filteredResponse.getWriter().write("accepted"));

                assertThat(created.get()).isEqualTo(1);
                assertThat(request.getAttribute("filtered")).isEqualTo("orders");
                assertThat(response.getContentAsString()).isEqualTo("accepted");
            } finally {
                proxy.destroy();
            }
        }
    }
}
