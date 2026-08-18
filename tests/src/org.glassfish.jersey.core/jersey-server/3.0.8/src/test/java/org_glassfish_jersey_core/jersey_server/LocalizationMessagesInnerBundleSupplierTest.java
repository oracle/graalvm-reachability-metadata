/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.l10n.Localizable;
import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationMessagesInnerBundleSupplierTest {
    @Test
    void loadsTheServerLocalizationBundleForTheRequestedLocale() {
        Localizable message = LocalizationMessages.localizableINIT_MSG("test");
        ResourceBundle bundle = message.getResourceBundle(Locale.getDefault());

        assertThat(bundle.getString(message.getKey())).isNotBlank();
    }

    @Test
    void bootstrapsAndInvokesAnnotatedResourcesAndProviders() throws Exception {
        ResourceConfig resourceConfig = new ResourceConfig(GreetingResource.class, ResponseHeaderProvider.class);
        ApplicationHandler handler = new ApplicationHandler(resourceConfig);

        try {
            ContainerRequest request = new ContainerRequest(
                    URI.create("http://localhost/"),
                    URI.create("http://localhost/greeting"),
                    "GET",
                    null,
                    new MapPropertiesDelegate());
            ContainerResponse response = handler.apply(request).get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getEntity()).isEqualTo("hello");
            assertThat(response.getHeaderString("X-Provider-Applied")).isEqualTo("true");
        } finally {
            handler.onShutdown(null);
        }
    }

    @Path("greeting")
    public static class GreetingResource {
        @GET
        public String greeting() {
            return "hello";
        }
    }

    @Provider
    public static class ResponseHeaderProvider implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
                throws IOException {
            responseContext.getHeaders().putSingle("X-Provider-Applied", "true");
        }
    }
}
