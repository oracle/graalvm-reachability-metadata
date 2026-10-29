/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextParameterInjectorInnerGenericDelegatingProxyTest {
    @Test
    void delegatesContextMethodsToTheCurrentRequest() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addSingletonResource(new HeaderResource());
            MockHttpResponse response = new MockHttpResponse();
            session.dispatcher.invoke(MockHttpRequest.get("/delegated?q=ok").header("X-Trace", "trace-7"), response);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("trace-7:ok");
        }
    }

    @Path("/delegated")
    public static class HeaderResource {
        @Context
        private HttpHeaders headers;
        @Context
        private UriInfo uri;

        @GET
        public String get() {
            return headers.getHeaderString("X-Trace") + ":" + uri.getQueryParameters().getFirst("q");
        }
    }
}
