/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceConfigTest {
    @Test
    void loadsConfiguredResourceClassDuringApplicationBootstrap() {
        ResourceConfig resourceConfig = new ResourceConfig()
                .property(ServerProperties.PROVIDER_CLASSNAMES, LoadedResource.class.getName());
        ApplicationHandler handler = new ApplicationHandler(resourceConfig);

        try {
            assertThat(resourceConfig.getClasses()).contains(LoadedResource.class);
            assertThat(handler.getConfiguration().isRegistered(LoadedResource.class)).isTrue();
        } finally {
            handler.onShutdown(null);
        }
    }

    @Path("loaded")
    public static class LoadedResource {
        @GET
        public String get() {
            return "loaded";
        }
    }
}
