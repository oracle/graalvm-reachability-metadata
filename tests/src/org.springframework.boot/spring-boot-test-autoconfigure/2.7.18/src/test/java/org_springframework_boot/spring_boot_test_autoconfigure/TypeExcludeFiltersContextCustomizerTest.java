/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeExcludeFiltersContextCustomizerTest {

    @BeforeEach
    void resetConstructorObservations() {
        ClassConstructorFilter.invocations.set(0);
        ClassConstructorFilter.testClass.set(null);
        NoArgumentConstructorFilter.invocations.set(0);
    }

    @Test
    void registersTypeExcludeFiltersCreatedWithSupportedConstructors() throws Exception {
        TestContextManager manager = new TestContextManager(FilteredContextTestCase.class);

        ApplicationContext context = manager.getTestContext().getApplicationContext();

        assertThat(context.containsBean(TypeExcludeFilters.class.getName())).isTrue();
        assertThat(context.getBean(TypeExcludeFilters.class.getName())).isInstanceOf(TypeExcludeFilter.class);
        assertThat(ClassConstructorFilter.invocations).hasValue(1);
        assertThat(ClassConstructorFilter.testClass).hasValue(FilteredContextTestCase.class);
        assertThat(NoArgumentConstructorFilter.invocations).hasValue(1);
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    @TypeExcludeFilters({ ClassConstructorFilter.class, NoArgumentConstructorFilter.class })
    static class FilteredContextTestCase {
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
    }

    static final class ClassConstructorFilter extends TypeExcludeFilter {

        static final AtomicInteger invocations = new AtomicInteger();

        static final AtomicReference<Class<?>> testClass = new AtomicReference<>();

        ClassConstructorFilter(Class<?> testClass) {
            invocations.incrementAndGet();
            ClassConstructorFilter.testClass.set(testClass);
        }

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
                throws IOException {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && getClass() == obj.getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

    }

    static final class NoArgumentConstructorFilter extends TypeExcludeFilter {

        static final AtomicInteger invocations = new AtomicInteger();

        NoArgumentConstructorFilter() {
            invocations.incrementAndGet();
        }

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
                throws IOException {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && getClass() == obj.getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

    }

}
