/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.annotation.Priority;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxrsInterceptorRegistryImplTest {
    @Test
    void buildsAndOrdersRequestFilterChain() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getProviderFactory().register(new LaterFilter());
            session.dispatcher.getProviderFactory().register(new EarlierFilter());
            session.dispatcher.getRegistry().addPerRequestResource(FilteredResource.class);
            MockHttpResponse response = session.get("/filtered");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("early:late");
        }
    }

    @Priority(100)
    public static class EarlierFilter implements ContainerRequestFilter {
        @Override
        public void filter(ContainerRequestContext request) {
            request.getHeaders().putSingle("X-Order", "early");
        }
    }

    @Priority(200)
    public static class LaterFilter implements ContainerRequestFilter {
        @Override
        public void filter(ContainerRequestContext request) {
            request.getHeaders().putSingle("X-Order", request.getHeaderString("X-Order") + ":late");
        }
    }

    @Path("/filtered")
    public static class FilteredResource {
        @GET
        public String get(@HeaderParam("X-Order") String order) {
            return order;
        }
    }
}
