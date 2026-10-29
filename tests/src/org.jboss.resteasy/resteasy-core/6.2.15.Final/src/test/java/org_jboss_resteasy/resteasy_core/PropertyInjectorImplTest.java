/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.PropertyInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyInjectorImplTest {
    @Test
    void injectsBeanFieldsAndSettersFromRequest() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(BeanResource.class);
            MockHttpResponse response = session.get("/bean?first=field&second=setter");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("field:setter");
        }
    }

    @Test
    void injectsContextFieldsAndSettersOutsideRequest() {
        try (DispatcherSession session = new DispatcherSession()) {
            ResteasyContext.pushContext(Label.class, new Label("outside"));
            ContextBean bean = new ContextBean();
            ResteasyProviderFactory factory = session.dispatcher.getProviderFactory();
            factory.injectProperties(bean);
            assertThat(bean.field.text).isEqualTo("outside");
            assertThat(bean.setter.text).isEqualTo("outside");
        }
    }

    @Test
    void injectsAsynchronousContextFieldsAndSettersInBothScopes() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            ResteasyProviderFactory factory = session.dispatcher.getProviderFactory();
            factory.register(AsyncContextFixture.class);
            PropertyInjector injector = factory.getInjectorFactory().createPropertyInjector(AsyncBean.class, factory);
            AsyncBean outside = new AsyncBean();
            injector.inject(outside, true).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(outside.field.text).isEqualTo("async-context");
            assertThat(outside.setter.text).isEqualTo("async-context");
            AsyncBean inside = new AsyncBean();
            injector.inject(MockHttpRequest.get("/async"), new MockHttpResponse(), inside, true)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(inside.field.text).isEqualTo("async-context");
            assertThat(inside.setter.text).isEqualTo("async-context");
        }
    }

    public static class AsyncBean {
        @Context
        private AsyncContextFixture.Label field;
        private AsyncContextFixture.Label setter;

        @Context
        public void setLabel(AsyncContextFixture.Label label) {
            setter = label;
        }
    }

    public static class QueryBean {
        @QueryParam("first")
        private String first;
        private String second;

        @QueryParam("second")
        public void setSecond(String second) {
            this.second = second;
        }
    }

    @Path("/bean")
    public static class BeanResource {
        @GET
        public String get(@BeanParam QueryBean bean) {
            return bean.first + ":" + bean.second;
        }
    }

    public static class Label {
        final String text;

        public Label(String text) {
            this.text = text;
        }
    }

    public static class ContextBean {
        @Context
        private Label field;
        private Label setter;

        @Context
        public void setLabel(Label label) {
            setter = label;
        }
    }
}
