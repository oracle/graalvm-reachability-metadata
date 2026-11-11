/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletRegistrationBeanTest {
    @Test
    void bindsInheritedRegistrationPropertiesAndRetainsTheConfiguredServlet() throws Exception {
        ServletRegistrationBean<OrdersServlet> registration = new ServletRegistrationBean<>(new OrdersServlet());
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "servlet.name", "orders", "servlet.async-supported", "false", "servlet.order", "12",
                "servlet.enabled", "true", "servlet.url-mappings[0]", "/orders/*",
                "servlet.init-parameters.region", "west")));

        assertThat(binder.bind("servlet", Bindable.ofInstance(registration)).get()).isSameAs(registration);
        assertThat(registration.getServletName()).isEqualTo("orders");
        assertThat(registration.isAsyncSupported()).isFalse();
        assertThat(registration.getOrder()).isEqualTo(12);
        assertThat(registration.isEnabled()).isTrue();
        assertThat(registration.getUrlMappings()).containsExactly("/orders/*");
        assertThat(registration.getInitParameters()).containsEntry("region", "west");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        registration.getServlet().service(request, response);
        assertThat(response.getHeader("X-Orders")).isEqualTo("ready");
    }

    public static class OrdersServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("X-Orders", "ready");
        }
    }
}
