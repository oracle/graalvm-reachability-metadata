/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.filter.annotation.StandardAnnotationCustomizableTypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;

public class TypeIncludesTest {
    @Test
    void includesTypesDeclaredInTheAnnotationIncludesResource() throws Exception {
        SliceFilter filter = new SliceFilter(SliceFixture.class);
        filter.setBeanClassLoader(getClass().getClassLoader());
        CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory();
        MetadataReader included = readers.getMetadataReader(IncludedComponent.class.getName());

        assertThat(filter.match(included, readers)).isFalse();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Slice {
        Filter[] includeFilters() default {};

        Filter[] excludeFilters() default {};

        boolean useDefaultFilters() default true;
    }

    @Slice
    static class SliceFixture {}

    static class IncludedComponent {}

    static final class SliceFilter extends StandardAnnotationCustomizableTypeExcludeFilter<Slice> {
        SliceFilter(Class<?> testClass) {
            super(testClass);
        }
    }
}
