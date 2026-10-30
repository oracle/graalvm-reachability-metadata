/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.boot.test.context.filter.annotation.StandardAnnotationCustomizableTypeExcludeFilter;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.util.ReflectionUtils;
import org_springframework_boot.spring_boot_data_couchbase_test.scan.ScanComponents;
import org_springframework_boot.spring_boot_data_couchbase_test.scan.ScanComponents.Excluded;
import org_springframework_boot.spring_boot_data_couchbase_test.scan.ScanComponents.Included;
import org_springframework_boot.spring_boot_data_couchbase_test.scan.ScanComponents.Ordinary;

import static org.assertj.core.api.Assertions.assertThat;

/// Scans components through the type-exclude filter that `@DataCouchbaseTest` declares.
///
/// Spring Boot's filter context customizer factory returns no customizer in a native image, where
/// it expects Spring AOT output, so the filter is created here the way that customizer creates it.
public class DataCouchbaseTypeExcludeFilterTest {
    private static final String SLICE_FILTER =
            "org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTypeExcludeFilter";

    @Test
    void defaultSliceOmitsOrdinaryApplicationComponents() throws ReflectiveOperationException {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            scan(DefaultSlice.class, context);

            assertThat(context.getBeansOfType(Included.class)).isEmpty();
            assertThat(context.getBeansOfType(Excluded.class)).isEmpty();
            assertThat(context.getBeansOfType(Ordinary.class)).isEmpty();
        }
    }

    @Test
    void explicitIncludesAdmitComponentsWhileExplicitExcludesTakePrecedence() throws ReflectiveOperationException {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            scan(FilteredSlice.class, context);

            assertThat(context.getBeansOfType(Included.class)).hasSize(1);
            assertThat(context.getBean(Included.class).name()).isEqualTo("catalog");
            assertThat(context.getBeansOfType(Excluded.class)).isEmpty();
            assertThat(context.getBeansOfType(Ordinary.class)).isEmpty();
        }
    }

    /// Registers the slice filter where Spring Boot's `TypeExcludeFilter` finds its delegates, then
    /// scans the components package the way `@SpringBootApplication` component scanning does.
    private static void scan(Class<?> slice, GenericApplicationContext context) throws ReflectiveOperationException {
        context.getBeanFactory().registerSingleton(TypeExcludeFilters.class.getName(), createSliceFilter(slice));
        TypeExcludeFilter filter = new TypeExcludeFilter();
        filter.setBeanFactory(context.getBeanFactory());
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context);
        scanner.addExcludeFilter(filter);
        scanner.scan(ScanComponents.class.getPackageName());
        context.refresh();
    }

    /// Creates the filter named by the slice's `@TypeExcludeFilters` through its `Class` constructor,
    /// as Spring Boot's `TypeExcludeFiltersContextCustomizer` does.
    private static TypeExcludeFilter createSliceFilter(Class<?> slice) throws ReflectiveOperationException {
        TypeExcludeFilters filters = TestContextAnnotationUtils.findMergedAnnotation(slice, TypeExcludeFilters.class);
        assertThat(filters).isNotNull();
        assertThat(filters.value()).hasSize(1);
        Class<?> filterClass = filters.value()[0];
        assertThat(filterClass.getName()).isEqualTo(SLICE_FILTER);
        assertThat(StandardAnnotationCustomizableTypeExcludeFilter.class).isAssignableFrom(filterClass);

        Constructor<?> constructor = filterClass.getDeclaredConstructor(Class.class);
        ReflectionUtils.makeAccessible(constructor);
        return (TypeExcludeFilter) constructor.newInstance(slice);
    }

    @DataCouchbaseTest
    public static class DefaultSlice {
    }

    @DataCouchbaseTest(
            includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Included.class),
            excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Excluded.class))
    public static class FilteredSlice {
    }
}
