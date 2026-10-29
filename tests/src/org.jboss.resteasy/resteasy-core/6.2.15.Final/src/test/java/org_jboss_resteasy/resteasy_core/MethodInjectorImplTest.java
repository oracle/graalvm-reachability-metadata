/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodInjectorImplTest {
    @Test
    void invokesResourceImplementingAnInterface() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(EchoResource.class);
            MockHttpResponse response = session.get("/method?text=hello");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("echo:hello");
        }
    }

    public interface Echo {
        String echo(String text);
    }

    @Path("/method")
    public static class EchoResource implements Echo {
        @GET
        @Override
        public String echo(@QueryParam("text") String text) {
            return "echo:" + text;
        }
    }
}
