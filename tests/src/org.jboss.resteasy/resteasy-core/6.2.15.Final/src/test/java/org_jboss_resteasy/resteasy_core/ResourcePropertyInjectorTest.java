/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.PropertyInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcePropertyInjectorTest {
    @Test
    void injectsResourceFieldsAndSetters() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(InjectedResource.class);
            MockHttpResponse response = new MockHttpResponse();
            session.dispatcher.invoke(MockHttpRequest.get("/properties?name=Ada").header("X-Region", "west"), response);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("Ada:west:/properties");
        }
    }

    @Test
    void injectsSingletonContextOutsideRequestAndResolvesItPerRequest() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addSingletonResource(new SingletonResource());
            assertThat(session.get("/singleton?name=one").getContentAsString()).isEqualTo("one");
            assertThat(session.get("/singleton?name=two").getContentAsString()).isEqualTo("two");
        }
    }

    @Test
    void injectsAsynchronousResourceContextInBothScopes() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            ResteasyProviderFactory factory = session.dispatcher.getProviderFactory();
            factory.register(AsyncContextFixture.class);
            ResourceClass resourceClass = factory.getResourceBuilder().getRootResourceFromAnnotations(AsyncResource.class);
            PropertyInjector injector = factory.getInjectorFactory().createPropertyInjector(resourceClass, factory);
            AsyncResource outside = new AsyncResource();
            injector.inject(outside, true).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(outside.field.text).isEqualTo("async-context");
            assertThat(outside.setter.text).isEqualTo("async-context");
            AsyncResource inside = new AsyncResource();
            injector.inject(MockHttpRequest.get("/async-resource"), new MockHttpResponse(), inside, true)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(inside.field.text).isEqualTo("async-context");
            assertThat(inside.setter.text).isEqualTo("async-context");
        }
    }

    @Path("/async-resource")
    public static class AsyncResource {
        @Context
        private AsyncContextFixture.Label field;
        private AsyncContextFixture.Label setter;

        @Context
        public void setLabel(AsyncContextFixture.Label label) {
            setter = label;
        }

        @GET
        public String get() {
            return field.text + ":" + setter.text;
        }
    }

    @Path("/properties")
    public static class InjectedResource {
        @QueryParam("name")
        private String name;
        @Context
        private UriInfo uri;
        private String region;

        @HeaderParam("X-Region")
        public void setRegion(String region) {
            this.region = region;
        }

        @GET
        public String get() {
            return name + ":" + region + ":" + uri.getPath();
        }
    }

    @Path("/singleton")
    public static class SingletonResource {
        @Context
        private UriInfo field;
        private UriInfo setter;

        @Context
        public void setUri(UriInfo uri) {
            setter = uri;
        }

        @GET
        public String get() {
            assertThat(field.getRequestUri()).isEqualTo(setter.getRequestUri());
            return setter.getQueryParameters().getFirst("name");
        }
    }
}
