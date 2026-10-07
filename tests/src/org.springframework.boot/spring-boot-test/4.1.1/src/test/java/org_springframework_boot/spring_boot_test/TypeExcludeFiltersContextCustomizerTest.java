/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.test.context.TestContextManager;

public class TypeExcludeFiltersContextCustomizerTest {
    @Test
    void createsAndRegistersBothSupportedFilterShapes() throws Exception {
        try (ConfigurableApplicationContext context = load(FilterFixture.class)) {
            TypeExcludeFilter filter =
                    context.getBean(TypeExcludeFilters.class.getName(), TypeExcludeFilter.class);
            CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory();
            MetadataReader marker = readers.getMetadataReader(ExcludedMarker.class.getName());

            assertThat(NoArgsFilter.created).isTrue();
            assertThat(TestClassFilter.testClass).isEqualTo(FilterFixture.class);
            assertThat(filter.match(marker, readers)).isTrue();
        }
    }

    private ConfigurableApplicationContext load(Class<?> testClass) {
        TestContextManager manager = new TestContextManager(testClass);
        return (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext();
    }

    @SpringBootTest(classes = TestConfiguration.class)
    @TypeExcludeFilters({NoArgsFilter.class, TestClassFilter.class})
    static class FilterFixture {}

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {}

    static class ExcludedMarker {}

    public static final class NoArgsFilter extends TypeExcludeFilter {
        static boolean created;

        public NoArgsFilter() {
            created = true;
        }

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
                throws IOException {
            return metadataReader.getClassMetadata().getClassName().equals(ExcludedMarker.class.getName());
        }
    }

    public static final class TestClassFilter extends TypeExcludeFilter {
        static Class<?> testClass;

        public TestClassFilter(Class<?> testClass) {
            TestClassFilter.testClass = testClass;
        }
    }
}
