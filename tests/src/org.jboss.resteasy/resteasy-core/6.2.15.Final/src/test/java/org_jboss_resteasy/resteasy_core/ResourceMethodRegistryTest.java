/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceMethodRegistryTest {
    @Test
    void addsDispatchesAndRemovesResourceRegistrations() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            Registry registry = session.dispatcher.getRegistry();
            registry.addPerRequestResource(RegisteredResource.class);
            assertThat(registry.getSize()).isEqualTo(1);
            assertThat(session.get("/registered").getContentAsString()).isEqualTo("registered");
            registry.removeRegistrations(RegisteredResource.class);
            assertThat(registry.getSize()).isZero();
            assertThat(session.get("/registered").getStatus()).isEqualTo(404);
        }
    }

    @Path("/registered")
    public static class RegisteredResource {
        @GET
        public String get() {
            return "registered";
        }
    }
}
