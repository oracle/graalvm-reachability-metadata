/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.Resource;

public class ConfigurationClassBeanDefinitionReaderTest {

    @Test
    void importedResourceUsesDeclaredBeanDefinitionReaderConstructor() {
        CountingBeanDefinitionReader.reset();

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ImportedResourceConfiguration.class)) {
            assertTrue(context.containsBeanDefinition(CountingBeanDefinitionReader.BEAN_NAME));
            assertEquals(1, CountingBeanDefinitionReader.constructorCalls());
            assertEquals(1, CountingBeanDefinitionReader.loadCalls());
        }
    }

    @Configuration
    @ImportResource(value = "classpath:org_springframework/spring_context/imported-resource.custom",
            reader = CountingBeanDefinitionReader.class)
    public static class ImportedResourceConfiguration {
    }

    public static class CountingBeanDefinitionReader extends AbstractBeanDefinitionReader {

        static final String BEAN_NAME = "importedResourceMarker";

        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger LOAD_CALLS = new AtomicInteger();

        public CountingBeanDefinitionReader(BeanDefinitionRegistry registry) {
            super(registry);
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        @Override
        public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
            LOAD_CALLS.incrementAndGet();

            RootBeanDefinition beanDefinition = new RootBeanDefinition(ImportedResourceMarker.class);
            beanDefinition.setLazyInit(true);
            getRegistry().registerBeanDefinition(BEAN_NAME, beanDefinition);
            return 1;
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            LOAD_CALLS.set(0);
        }

        static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        static int loadCalls() {
            return LOAD_CALLS.get();
        }
    }

    public static class ImportedResourceMarker {
    }
}
