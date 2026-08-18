/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.TypeFilterUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public class ParserStrategyUtilsTest {

    @Test
    void customTypeFilterUsesDeclaredConstructorAndAwareCallbacks() throws IOException {
        ClassLoader classLoader = ParserStrategyUtilsTest.class.getClassLoader();
        Environment environment = new StandardEnvironment();
        ResourceLoader resourceLoader = new DefaultResourceLoader(classLoader);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.setBeanClassLoader(classLoader);

        List<TypeFilter> filters = TypeFilterUtils.createTypeFiltersFor(customFilterAttributes(), environment,
                resourceLoader, registry);

        assertEquals(1, filters.size());
        assertTrue(filters.get(0) instanceof CapturingTypeFilter);
        CapturingTypeFilter filter = (CapturingTypeFilter) filters.get(0);
        assertSame(environment, filter.constructorEnvironment);
        assertSame(resourceLoader, filter.constructorResourceLoader);
        assertSame(registry, filter.constructorBeanFactory);
        assertSame(classLoader, filter.constructorClassLoader);
        assertSame(environment, filter.awareEnvironment);
        assertSame(resourceLoader, filter.awareResourceLoader);
        assertSame(registry, filter.awareBeanFactory);
        assertSame(classLoader, filter.awareClassLoader);
        assertFalse(filter.match(null, null));
    }

    private static AnnotationAttributes customFilterAttributes() {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put("type", FilterType.CUSTOM);
        attributes.put("classes", new Class<?>[] {CapturingTypeFilter.class});
        attributes.put("pattern", new String[0]);
        return attributes;
    }

    public static class CapturingTypeFilter implements TypeFilter, BeanClassLoaderAware, BeanFactoryAware,
            EnvironmentAware, ResourceLoaderAware {

        final Environment constructorEnvironment;

        final ResourceLoader constructorResourceLoader;

        final BeanFactory constructorBeanFactory;

        final ClassLoader constructorClassLoader;

        Environment awareEnvironment;

        ResourceLoader awareResourceLoader;

        BeanFactory awareBeanFactory;

        ClassLoader awareClassLoader;

        public CapturingTypeFilter(Environment environment, ResourceLoader resourceLoader, BeanFactory beanFactory,
                ClassLoader classLoader) {
            this.constructorEnvironment = environment;
            this.constructorResourceLoader = resourceLoader;
            this.constructorBeanFactory = beanFactory;
            this.constructorClassLoader = classLoader;
        }

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
                throws IOException {
            return false;
        }

        @Override
        public void setBeanClassLoader(ClassLoader classLoader) {
            this.awareClassLoader = classLoader;
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.awareBeanFactory = beanFactory;
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.awareEnvironment = environment;
        }

        @Override
        public void setResourceLoader(ResourceLoader resourceLoader) {
            this.awareResourceLoader = resourceLoader;
        }
    }
}
