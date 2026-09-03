/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.AbortedResponse;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.junit.jupiter.api.Test;

public class ProxyBuilderTest {

    @Test
    void buildsAndInvokesProxyUsingContextClassLoader() {
        RecordingClientHttpEngine engine = new RecordingClientHttpEngine();

        try (ResteasyClient client = new ResteasyClientBuilderImpl().httpEngine(engine).build()) {
            ResourceApi proxy = ProxyBuilder.builder(ResourceApi.class, client.target("http://localhost:8080"))
                    .build();

            try (Response response = proxy.fetch()) {
                assertThat(response.getStatus()).isEqualTo(200);
            }
        }

        assertThat(engine.invocation.getUri().getPath()).isEqualTo("/resources/current");
    }

    @Test
    void buildsProxyWhenContextClassLoaderCannotSeeImplementation() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        RecordingClientHttpEngine engine = new RecordingClientHttpEngine();

        try (ResteasyClient client = new ResteasyClientBuilderImpl().httpEngine(engine).build()) {
            Thread.currentThread()
                    .setContextClassLoader(new ImplementationBlockingClassLoader(ProxyBuilder.class.getClassLoader()));
            try {
                ResourceApi proxy = ProxyBuilder.builder(ResourceApi.class, client.target("http://localhost:8080"))
                        .build();

                try (Response response = proxy.fetch()) {
                    assertThat(response.getStatus()).isEqualTo(200);
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalLoader);
            }
        }

        assertThat(engine.invocation.getUri().getPath()).isEqualTo("/resources/current");
    }

    @Path("/resources")
    public interface ResourceApi {

        @GET
        @Path("/current")
        Response fetch();
    }

    private static final class ImplementationBlockingClassLoader extends ClassLoader {

        private static final String PROXY_BUILDER_IMPLEMENTATION =
                "org.jboss.resteasy.client.jaxrs.internal.proxy.ProxyBuilderImpl";

        private ImplementationBlockingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (PROXY_BUILDER_IMPLEMENTATION.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }

    private static final class RecordingClientHttpEngine implements ClientHttpEngine {

        private ClientInvocation invocation;

        @Override
        public SSLContext getSslContext() {
            return null;
        }

        @Override
        public HostnameVerifier getHostnameVerifier() {
            return null;
        }

        @Override
        public Response invoke(Invocation request) {
            invocation = (ClientInvocation) request;
            return new AbortedResponse(invocation.getClientConfiguration(), Response.ok().build());
        }

        @Override
        public void close() {
        }
    }
}
