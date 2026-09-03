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

public class ArrayExtractorTest {
    @Test
    void extractsProvidedDefaultedAndAbsentQueryParameterArrays() throws Exception {
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig(ArrayResource.class));

        try {
            ContainerRequest request = new ContainerRequest(
                    URI.create("http://localhost/"),
                    URI.create("http://localhost/arrays?provided=one&provided=two"),
                    "GET",
                    null,
                    new MapPropertiesDelegate());

            ContainerResponse response = handler.apply(request).get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getEntity()).isEqualTo("one,two|fallback|0");
        } finally {
            handler.onShutdown(null);
        }
    }

    @Path("arrays")
    public static class ArrayResource {
        @GET
        public String arrays(
                @QueryParam("provided") String[] provided,
                @DefaultValue("fallback") @QueryParam("defaulted") String[] defaulted,
                @QueryParam("absent") String[] absent) {
            return String.join(",", provided) + "|" + String.join(",", defaulted) + "|" + absent.length;
        }
    }
}
