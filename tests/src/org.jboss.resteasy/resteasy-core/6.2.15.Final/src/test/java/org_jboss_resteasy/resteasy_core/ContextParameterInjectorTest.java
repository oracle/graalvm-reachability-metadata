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
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextParameterInjectorTest {
    @Test
    void injectsRequestScopedUriProxyIntoSingleton() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addSingletonResource(new ContextResource());
            MockHttpResponse first = session.get("/context?value=first");
            MockHttpResponse second = session.get("/context?value=second");
            assertThat(first.getStatus()).isEqualTo(200);
            assertThat(second.getStatus()).isEqualTo(200);
            assertThat(first.getContentAsString()).isEqualTo("/context:first");
            assertThat(second.getContentAsString()).isEqualTo("/context:second");
        }
    }

    @Path("/context")
    public static class ContextResource {
        @Context
        private UriInfo uri;

        @GET
        public String get() {
            return uri.getPath() + ":" + uri.getQueryParameters().getFirst("value");
        }
    }
}
