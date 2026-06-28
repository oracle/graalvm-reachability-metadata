/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.spi.Manager;
import org.jboss.arquillian.core.spi.ManagerBuilder;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.impl.EventTestRunnerAdaptor;
import org.jboss.arquillian.test.impl.TestContextHandler;
import org.jboss.arquillian.test.impl.TestInstanceEnricher;
import org.jboss.arquillian.test.impl.context.ClassContextImpl;
import org.jboss.arquillian.test.impl.context.SuiteContextImpl;
import org.jboss.arquillian.test.impl.context.TestContextImpl;
import org.jboss.arquillian.test.impl.enricher.resource.ArquillianResourceTestEnricher;
import org.jboss.arquillian.test.spi.LifecycleMethodExecutor;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.junit.jupiter.api.Test;

public class ArquillianResourceTestEnricherTest {
    @Test
    void beforeLifecycleEnrichesArquillianResourceField() throws Exception {
        Manager manager = createStartedManagerWithResourceProvider();
        EventTestRunnerAdaptor adaptor = new EventTestRunnerAdaptor(manager);
        try {
            ResourceFieldTestCase testCase = new ResourceFieldTestCase();
            Method testMethod = ResourceFieldTestCase.class.getDeclaredMethod("exampleTest");

            adaptor.before(testCase, testMethod, LifecycleMethodExecutor.NO_OP);

            assertThat(testCase.resource()).isEqualTo("injected class-scoped resource");
        } finally {
            adaptor.shutdown();
        }
    }

    private static Manager createStartedManagerWithResourceProvider() {
        Manager manager = ManagerBuilder.from()
                .context(SuiteContextImpl.class)
                .context(ClassContextImpl.class)
                .context(TestContextImpl.class)
                .extensions(TestContextHandler.class, TestInstanceEnricher.class)
                .create();
        manager.start();

        FixedServiceLoader serviceLoader = new FixedServiceLoader();
        manager.bind(ApplicationScoped.class, ServiceLoader.class, serviceLoader);

        ArquillianResourceTestEnricher enricher = new ArquillianResourceTestEnricher();
        manager.inject(enricher);
        serviceLoader.add(TestEnricher.class, enricher);
        serviceLoader.add(ResourceProvider.class, new StringResourceProvider());
        return manager;
    }

    private static final class ResourceFieldTestCase {
        @ArquillianResource
        private String resource;

        private String resource() {
            return resource;
        }

        public void exampleTest() {
        }
    }

    private static final class StringResourceProvider implements ResourceProvider {
        @Override
        public boolean canProvide(Class<?> type) {
            return String.class.equals(type);
        }

        @Override
        public Object lookup(ArquillianResource resource, Annotation... qualifiers) {
            boolean classScopedInjection = false;
            boolean methodScopedInjection = false;
            for (Annotation qualifier : qualifiers) {
                if (ResourceProvider.ClassInjection.class.equals(qualifier.annotationType())) {
                    classScopedInjection = true;
                }
                if (ResourceProvider.MethodInjection.class.equals(qualifier.annotationType())) {
                    methodScopedInjection = true;
                }
            }
            assertThat(resource.value()).isEqualTo(ArquillianResource.class);
            assertThat(classScopedInjection).isTrue();
            assertThat(methodScopedInjection).isFalse();
            return "injected class-scoped resource";
        }
    }

    private static final class FixedServiceLoader implements ServiceLoader {
        private final Map<Class<?>, List<Object>> services = new HashMap<Class<?>, List<Object>>();

        private <T> void add(Class<T> serviceClass, T service) {
            List<Object> registeredServices = services.get(serviceClass);
            if (registeredServices == null) {
                registeredServices = new ArrayList<Object>();
                services.put(serviceClass, registeredServices);
            }
            registeredServices.add(service);
        }

        @Override
        public <T> Collection<T> all(Class<T> serviceClass) {
            List<Object> registeredServices = services.get(serviceClass);
            if (registeredServices == null) {
                return Collections.emptyList();
            }
            List<T> typedServices = new ArrayList<T>();
            for (Object service : registeredServices) {
                typedServices.add(serviceClass.cast(service));
            }
            return typedServices;
        }

        @Override
        public <T> T onlyOne(Class<T> serviceClass) {
            Collection<T> registeredServices = all(serviceClass);
            if (registeredServices.isEmpty()) {
                return null;
            }
            if (registeredServices.size() > 1) {
                throw new IllegalStateException("Multiple service implementations found for " + serviceClass.getName());
            }
            return registeredServices.iterator().next();
        }

        @Override
        public <T> T onlyOne(Class<T> serviceClass, Class<? extends T> defaultServiceClass) {
            T service = onlyOne(serviceClass);
            if (service == null) {
                throw new IllegalStateException("No service implementation found for " + serviceClass.getName());
            }
            return service;
        }
    }
}
