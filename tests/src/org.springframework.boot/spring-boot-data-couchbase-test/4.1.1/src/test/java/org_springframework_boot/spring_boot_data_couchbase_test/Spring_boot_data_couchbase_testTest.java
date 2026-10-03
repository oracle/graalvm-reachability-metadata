/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.couchbase.autoconfigure.CouchbaseAutoConfiguration;
import org.springframework.boot.data.couchbase.test.autoconfigure.AutoConfigureDataCouchbase;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.test.context.TestContextAnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_couchbase_testTest {

    @Test
    void resolvesConfiguredDataCouchbaseSliceThroughSpringTestContext() {
        DataCouchbaseTest dataCouchbaseTest = TestContextAnnotationUtils.findMergedAnnotation(
                ConfiguredDataCouchbaseSlice.class, DataCouchbaseTest.class);

        assertThat(dataCouchbaseTest).isNotNull();
        assertThat(dataCouchbaseTest.properties())
                .containsExactly("spring.data.couchbase.bucket-name=test-bucket");
        assertThat(dataCouchbaseTest.useDefaultFilters()).isFalse();
        assertThat(dataCouchbaseTest.includeFilters()).singleElement().satisfies(filter -> {
            assertThat(filter.type()).isEqualTo(FilterType.ASSIGNABLE_TYPE);
            assertThat(filter.classes()).containsExactly(IncludedComponent.class);
        });
        assertThat(dataCouchbaseTest.excludeFilters()).singleElement().satisfies(filter -> {
            assertThat(filter.type()).isEqualTo(FilterType.ASSIGNABLE_TYPE);
            assertThat(filter.classes()).containsExactly(ExcludedComponent.class);
        });
        assertThat(dataCouchbaseTest.excludeAutoConfiguration())
                .containsExactly(CouchbaseAutoConfiguration.class);

        MergedAnnotations annotations = MergedAnnotations.from(ConfiguredDataCouchbaseSlice.class);
        assertThat(annotations.isPresent(AutoConfigureDataCouchbase.class)).isTrue();
        assertThat(annotations.isPresent(ImportAutoConfiguration.class)).isTrue();
    }

    @DataCouchbaseTest(
            properties = "spring.data.couchbase.bucket-name=test-bucket",
            useDefaultFilters = false,
            includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = IncludedComponent.class),
            excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = ExcludedComponent.class),
            excludeAutoConfiguration = CouchbaseAutoConfiguration.class)
    static class ConfiguredDataCouchbaseSlice {
    }

    static class IncludedComponent {
    }

    static class ExcludedComponent {
    }

}
