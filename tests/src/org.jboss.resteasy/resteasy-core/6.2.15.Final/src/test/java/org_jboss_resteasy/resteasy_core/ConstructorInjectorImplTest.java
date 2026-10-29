/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorInjectorImplTest {
    @Test
    void constructsPerRequestResourceWithPathArgument() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(ConstructedResource.class);
            MockHttpResponse response = session.get("/constructed/42");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("item-42");
        }
    }

    @Test
    void constructsInjectedObjectOutsideRequest() {
        try (DispatcherSession session = new DispatcherSession()) {
            ResteasyContext.pushContext(Settings.class, new Settings("configured"));
            Service service = session.dispatcher.getProviderFactory().injectedInstance(Service.class);
            assertThat(service.settings.name).isEqualTo("configured");
        }
    }

    @Path("/constructed/{id}")
    public static class ConstructedResource {
        private final int id;

        public ConstructedResource(@PathParam("id") int id) {
            this.id = id;
        }

        @GET
        public String get() {
            return "item-" + id;
        }
    }

    public static class Settings {
        final String name;

        public Settings(String name) {
            this.name = name;
        }
    }

    public static class Service {
        final Settings settings;

        public Service(@Context Settings settings) {
            this.settings = settings;
        }
    }
}
