/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointServlet;
import org.springframework.boot.actuate.endpoint.web.ExposableServletEndpoint;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpoint;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointDiscoverer;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ServletEndpointDiscovererTest {

    @Test
    void discoversTheSuppliedServletAndMapsItsManagementLink() {
        StatusServletEndpoint bean = new StatusServletEndpoint();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(StatusServletEndpoint.class, () -> bean);
            context.refresh();
            ServletEndpointDiscoverer discoverer = new ServletEndpointDiscoverer(context,
                    List.of(id -> "legacy/" + id), List.of());

            Collection<ExposableServletEndpoint> endpoints = discoverer.getEndpoints();

            assertThat(endpoints).hasSize(1);
            ExposableServletEndpoint endpoint = endpoints.iterator().next();
            assertThat(endpoint.getEndpointId().toString()).isEqualTo("status");
            assertThat(endpoint.getDefaultAccess()).isEqualTo(Access.READ_ONLY);
            assertThat(endpoint.getRootPath()).isEqualTo("legacy/status");
            assertThat(endpoint.getOperations()).isEmpty();
            assertThat(endpoint.getEndpointServlet()).isSameAs(bean.servlet);
            int supplied = bean.supplied;
            assertThat(supplied).isPositive();
            assertThat(new EndpointLinksResolver(endpoints).resolveLinks("https://example.test/manage")
                    .get("status").getHref()).isEqualTo("https://example.test/manage/legacy/status");
            assertThat(discoverer.getEndpoints()).isSameAs(endpoints);
            assertThat(bean.supplied).isEqualTo(supplied);
        }
    }

    @ServletEndpoint(id = "status", defaultAccess = Access.READ_ONLY)
    public static class StatusServletEndpoint implements Supplier<EndpointServlet> {

        private final EndpointServlet servlet = new EndpointServlet(StatusServlet.class)
                .withInitParameter("mode", "summary").withLoadOnStartup(1);
        private int supplied;

        @Override
        public EndpointServlet get() {
            this.supplied++;
            return this.servlet;
        }
    }

    public static class StatusServlet extends HttpServlet {
    }
}
