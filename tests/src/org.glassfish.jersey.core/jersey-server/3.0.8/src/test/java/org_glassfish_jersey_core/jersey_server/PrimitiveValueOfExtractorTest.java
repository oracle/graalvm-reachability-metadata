/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrimitiveValueOfExtractorTest {
    @Test
    void extractsPrimitiveQueryParameterUsingItsValueOfMethod() throws Exception {
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig(PrimitiveResource.class));

        try {
            ContainerRequest request = new ContainerRequest(
                    URI.create("http://localhost/"),
                    URI.create("http://localhost/primitives?count=42"),
                    "GET",
                    null,
                    new MapPropertiesDelegate());

            ContainerResponse response = handler.apply(request).get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getEntity()).isEqualTo("42");
        } finally {
            handler.onShutdown(null);
        }
    }

    @Path("primitives")
    public static class PrimitiveResource {
        @GET
        public String get(@DefaultValue("7") @QueryParam("count") int count) {
            return Integer.toString(count);
        }
    }
}
