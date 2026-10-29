/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;

import org.jboss.resteasy.core.InjectorFactoryImpl;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ResteasyDeploymentImplTest {
    @Test
    void deploysNamedApplicationProvidersResourcesAndContextObjects() throws Exception {
        ResteasyProviderFactory previous = ResteasyProviderFactory.peekInstance();
        ResteasyDeploymentImpl deployment = new ResteasyDeploymentImpl();
        deployment.setApplicationClass(EmptyApplication.class.getName());
        deployment.setInjectorFactoryClass(InjectorFactoryImpl.class.getName());
        deployment.getConstructedDefaultContextObjects().put(Greeting.class.getName(), Greeting.class.getName());
        deployment.getProviderClasses().add(ResponseTag.class.getName());
        deployment.getResourceClasses().add(NamedResource.class.getName());
        deployment.getScannedResourceClasses().add(ScannedResource.class.getName());
        deployment.getScannedResourceClassesWithBuilder().put(ResourceBuilder.class.getName(), List.of(BuiltResource.class.getName()));
        try {
            deployment.start();
            for (String path : List.of("/named", "/scanned", "/built")) {
                MockHttpResponse response = new MockHttpResponse();
                deployment.getDispatcher().invoke(MockHttpRequest.get(path), response);
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getContentAsString()).isEqualTo("hello");
                assertThat(response.getOutputHeaders().getFirst("X-Deployment")).isEqualTo("configured");
            }
            assertThat(deployment.getApplication()).isInstanceOf(EmptyApplication.class);
            assertThat(deployment.getProviderFactory().getInjectorFactory()).isInstanceOf(InjectorFactoryImpl.class);
        } finally {
            deployment.stop();
            ResteasyProviderFactory.setInstance(previous);
        }
    }

    public static class EmptyApplication extends Application {
    }

    public static class Greeting {
        public String value() {
            return "hello";
        }
    }

    public static class ResponseTag implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            response.getHeaders().putSingle("X-Deployment", "configured");
        }
    }

    @Path("/named")
    public static class NamedResource {
        @Context
        private Greeting greeting;

        @GET
        public String get() {
            return greeting.value();
        }
    }

    @Path("/scanned")
    public static class ScannedResource extends NamedResource {
    }

    @Path("/built")
    public static class BuiltResource extends NamedResource {
    }
}
