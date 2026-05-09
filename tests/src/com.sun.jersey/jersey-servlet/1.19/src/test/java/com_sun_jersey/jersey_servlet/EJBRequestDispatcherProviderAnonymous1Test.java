/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.AbstractResource;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.core.spi.component.ProviderFactory;
import com.sun.jersey.core.spi.component.ProviderServices;
import com.sun.jersey.core.spi.factory.InjectableProviderFactory;
import com.sun.jersey.server.impl.ejb.EJBRequestDispatcherProvider;
import com.sun.jersey.spi.container.JavaMethodInvoker;
import com.sun.jersey.spi.container.ResourceMethodCustomInvokerDispatchFactory;
import com.sun.jersey.spi.container.ResourceMethodCustomInvokerDispatchProvider;
import com.sun.jersey.spi.dispatch.RequestDispatcher;
import com.sun.jersey.spi.inject.Errors;
import com.sun.jersey.spi.inject.SingletonTypeInjectableProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ws.rs.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EJBRequestDispatcherProviderAnonymous1Test {
    @Test
    void dispatcherInvokesMatchedRemoteInterfaceMethod() throws NoSuchMethodException {
        Method method = PingBean.class.getMethod("ping");
        AbstractResource resource = new AbstractResource(PingBean.class);
        AbstractResourceMethod resourceMethod = new AbstractResourceMethod(
                resource,
                method,
                void.class,
                void.class,
                "GET",
                new java.lang.annotation.Annotation[0]);
        EJBRequestDispatcherProvider provider = new EJBRequestDispatcherProvider();
        injectDispatcherFactory(provider);

        RequestDispatcher dispatcher = Errors.processWithErrors(new Errors.Closure<RequestDispatcher>() {
            @Override
            public RequestDispatcher f() {
                return provider.create(resourceMethod);
            }
        });
        assertThat(dispatcher).isNotNull();
        PingBean bean = new PingBean();
        dispatcher.dispatch(bean, null);

        assertThat(bean.isPinged()).isTrue();
    }

    private static void injectDispatcherFactory(EJBRequestDispatcherProvider provider) {
        InjectableProviderFactory injectableProviderFactory = new InjectableProviderFactory();
        ProviderFactory providerFactory = new ProviderFactory(injectableProviderFactory);
        ResourceMethodCustomInvokerDispatchFactory dispatchFactory = createDispatchFactory(providerFactory);
        injectableProviderFactory.add(
                new SingletonTypeInjectableProvider<Context, ResourceMethodCustomInvokerDispatchFactory>(
                        ResourceMethodCustomInvokerDispatchFactory.class,
                        dispatchFactory) {
                });
        providerFactory.injectOnProviderInstance(provider);
    }

    private static ResourceMethodCustomInvokerDispatchFactory createDispatchFactory(ProviderFactory providerFactory) {
        Set<Object> providerInstances = new LinkedHashSet<Object>();
        providerInstances.add(new InvokingDispatchProvider());
        ProviderServices providerServices = new ProviderServices(
                providerFactory,
                Collections.<Class<?>>emptySet(),
                providerInstances);
        return new ResourceMethodCustomInvokerDispatchFactory(providerServices);
    }

    public interface PingRemote {
        void ping();
    }

    @Stateless
    @Remote(PingRemote.class)
    public static final class PingBean implements PingRemote {
        private boolean pinged;

        @Override
        public void ping() {
            pinged = true;
        }

        private boolean isPinged() {
            return pinged;
        }
    }

    private static final class InvokingDispatchProvider implements ResourceMethodCustomInvokerDispatchProvider {
        @Override
        public RequestDispatcher create(AbstractResourceMethod resourceMethod, JavaMethodInvoker invoker) {
            return new InvokingRequestDispatcher(resourceMethod, invoker);
        }
    }

    private static final class InvokingRequestDispatcher implements RequestDispatcher {
        private final AbstractResourceMethod resourceMethod;
        private final JavaMethodInvoker invoker;

        private InvokingRequestDispatcher(AbstractResourceMethod resourceMethod, JavaMethodInvoker invoker) {
            this.resourceMethod = resourceMethod;
            this.invoker = invoker;
        }

        @Override
        public void dispatch(Object resource, HttpContext context) {
            try {
                invoker.invoke(resourceMethod.getMethod(), resource);
            } catch (InvocationTargetException | IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
    }
}
