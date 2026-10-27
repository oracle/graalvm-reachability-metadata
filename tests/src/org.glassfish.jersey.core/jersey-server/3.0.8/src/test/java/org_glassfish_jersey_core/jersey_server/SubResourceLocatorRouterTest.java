/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SubResourceLocatorRouterTest {
    @Test
    void invokesSubResourceLocatorAndMatchedResourceMethod() throws Exception {
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig(RootResource.class));

        try {
            ContainerRequest request = new ContainerRequest(
                    URI.create("http://localhost/"),
                    URI.create("http://localhost/root/child"),
                    "GET",
                    null,
                    new MapPropertiesDelegate());

            ContainerResponse response = handler.apply(request).get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getEntity()).isEqualTo("child resource");
        } finally {
            handler.onShutdown(null);
        }
    }

    @Path("root")
    public static class RootResource {
        @Path("child")
        public ChildResource child() {
            return new ChildResource();
        }
    }

    public static class ChildResource {
        @GET
        public String get() {
            return "child resource";
        }
    }
}
