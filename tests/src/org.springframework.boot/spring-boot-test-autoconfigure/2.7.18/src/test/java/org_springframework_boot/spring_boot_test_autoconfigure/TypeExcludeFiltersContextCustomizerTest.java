/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeExcludeFiltersContextCustomizerTest {

    @Test
    void createsFilterWithTheAnnotatedTestClass() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            customizeContext(ClassConstructorFixture.class, context);
            TypeExcludeFilter filter = context.getBeanFactory()
                    .getBean(TypeExcludeFilters.class.getName(), TypeExcludeFilter.class);
            SimpleMetadataReaderFactory readers = new SimpleMetadataReaderFactory();

            assertTrue(filter.match(readers.getMetadataReader(TypeExcludeFilters.class.getName()), readers));
            assertFalse(filter.match(readers.getMetadataReader(TypeExcludeFilter.class.getName()), readers));
        }
    }

    @Test
    void createsFilterWithANoArgumentConstructor() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            customizeContext(NoArgumentFixture.class, context);
            TypeExcludeFilter filter = context.getBeanFactory()
                    .getBean(TypeExcludeFilters.class.getName(), TypeExcludeFilter.class);
            SimpleMetadataReaderFactory readers = new SimpleMetadataReaderFactory();

            assertTrue(filter.match(readers.getMetadataReader(TypeExcludeFilter.class.getName()), readers));
            assertFalse(filter.match(readers.getMetadataReader(TypeExcludeFilters.class.getName()), readers));
        }
    }

    private static void customizeContext(Class<?> testClass, GenericApplicationContext context) {
        ContextCustomizerFactory factory = SpringFactoriesLoader
                .loadFactories(ContextCustomizerFactory.class, TypeExcludeFiltersContextCustomizerTest.class.getClassLoader())
                .stream()
                .filter(candidate -> candidate.getClass().getName().equals(
                        "org.springframework.boot.test.autoconfigure.filter.TypeExcludeFiltersContextCustomizerFactory"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Type exclude filters factory was not loaded"));
        ContextCustomizer customizer = factory.createContextCustomizer(testClass, List.of());
        assertNotNull(customizer);
        customizer.customizeContext(context, new MergedContextConfiguration(testClass, null, null, null, null));
    }

    @TypeExcludeFilters(ClassConstructorFilter.class)
    static class ClassConstructorFixture {
    }

    @TypeExcludeFilters(NoArgumentFilter.class)
    static class NoArgumentFixture {
    }

    public static final class ClassConstructorFilter extends TypeExcludeFilter {
        private final Class<?> testClass;

        public ClassConstructorFilter(Class<?> testClass) {
            this.testClass = testClass;
            assertSame(ClassConstructorFixture.class, testClass);
        }

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory readers) {
            return reader.getClassMetadata().getClassName().equals(TypeExcludeFilters.class.getName());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ClassConstructorFilter && this.testClass == ((ClassConstructorFilter) other).testClass;
        }

        @Override
        public int hashCode() {
            return this.testClass.hashCode();
        }
    }

    public static final class NoArgumentFilter extends TypeExcludeFilter {
        public NoArgumentFilter() {
        }

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory readers) {
            return reader.getClassMetadata().getClassName().equals(TypeExcludeFilter.class.getName());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NoArgumentFilter;
        }

        @Override
        public int hashCode() {
            return NoArgumentFilter.class.hashCode();
        }
    }
}
