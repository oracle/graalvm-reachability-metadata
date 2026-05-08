/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.spi.Manager;
import org.jboss.arquillian.core.spi.ManagerBuilder;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.impl.enricher.resource.ArquillianResourceTestEnricher;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.junit.jupiter.api.Test;

public class ArquillianResourceTestEnricherTest {
    private static final String RESOURCE_VALUE = "managed-resource";

    @Test
    void enrichInjectsResourceProviderValueIntoAnnotatedField() {
        Manager manager = ManagerBuilder.from().create();
        manager.start();
        try {
            manager.bind(ApplicationScoped.class, ServiceLoader.class,
                new SingleResourceProviderServiceLoader(new StringResourceProvider()));
            ArquillianResourceTestEnricher enricher = new ArquillianResourceTestEnricher();
            manager.inject(enricher);
            ResourceFieldTarget target = new ResourceFieldTarget();

            enricher.enrich(target);

            assertThat(target.resource()).isEqualTo(RESOURCE_VALUE);
        } finally {
            manager.shutdown();
        }
    }

    private static final class ResourceFieldTarget {
        @ArquillianResource
        private String resource;

        String resource() {
            return resource;
        }
    }

    private static final class StringResourceProvider implements ResourceProvider {
        @Override
        public boolean canProvide(Class<?> type) {
            return String.class.equals(type);
        }

        @Override
        public Object lookup(ArquillianResource resource, Annotation... qualifiers) {
            assertThat(resource).isNotNull();
            assertThat(Arrays.stream(qualifiers))
                .anyMatch(qualifier -> ResourceProvider.ClassInjection.class
                    .isAssignableFrom(qualifier.annotationType()));
            return RESOURCE_VALUE;
        }
    }

    private static final class SingleResourceProviderServiceLoader implements ServiceLoader {
        private final ResourceProvider resourceProvider;

        private SingleResourceProviderServiceLoader(ResourceProvider resourceProvider) {
            this.resourceProvider = resourceProvider;
        }

        @Override
        public <T> Collection<T> all(Class<T> serviceClass) {
            if (ResourceProvider.class.equals(serviceClass)) {
                return Collections.singleton(serviceClass.cast(resourceProvider));
            }
            return Collections.emptyList();
        }

        @Override
        public <T> T onlyOne(Class<T> serviceClass) {
            Collection<T> services = all(serviceClass);
            if (services.isEmpty()) {
                return null;
            }
            return services.iterator().next();
        }

        @Override
        public <T> T onlyOne(Class<T> serviceClass, Class<? extends T> defaultServiceClass) {
            return onlyOne(serviceClass);
        }
    }
}
