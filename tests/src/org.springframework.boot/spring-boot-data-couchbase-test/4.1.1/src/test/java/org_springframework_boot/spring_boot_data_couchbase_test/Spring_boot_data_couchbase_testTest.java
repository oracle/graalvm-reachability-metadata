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
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseRepositoriesAutoConfiguration;
import org.springframework.boot.data.couchbase.test.autoconfigure.AutoConfigureDataCouchbase;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.MergedAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_couchbase_testTest {

    @Test
    void dataCouchbaseTestExposesItsConfigurationAttributes() {
        DataCouchbaseTest annotation = MergedAnnotations.from(DataCouchbaseSlice.class)
                .get(DataCouchbaseTest.class)
                .synthesize();

        assertThat(annotation.properties()).containsExactly("spring.profiles.active=data-couchbase-slice");
        assertThat(annotation.useDefaultFilters()).isTrue();
        assertThat(annotation.includeFilters()).hasSize(1);
        assertThat(annotation.excludeFilters()).isEmpty();
        assertThat(annotation.excludeAutoConfiguration()).containsExactly(
                CouchbaseAutoConfiguration.class,
                DataCouchbaseAutoConfiguration.class,
                DataCouchbaseReactiveAutoConfiguration.class,
                DataCouchbaseReactiveRepositoriesAutoConfiguration.class,
                DataCouchbaseRepositoriesAutoConfiguration.class);
    }

    @Test
    void dataCouchbaseTestComposesTheExpectedSpringAnnotations() {
        MergedAnnotations annotations = MergedAnnotations.from(DataCouchbaseSlice.class);

        assertThat(annotations.isPresent(DataCouchbaseTest.class)).isTrue();
        assertThat(annotations.isPresent(AutoConfigureDataCouchbase.class)).isTrue();
        assertThat(annotations.isPresent(ImportAutoConfiguration.class)).isTrue();
    }

    @Test
    void autoConfigureDataCouchbaseIsAvailableAsAComposableAnnotation() {
        MergedAnnotations annotations = MergedAnnotations.from(AutoConfiguredComponent.class);

        assertThat(annotations.isPresent(AutoConfigureDataCouchbase.class)).isTrue();
        assertThat(annotations.isPresent(ImportAutoConfiguration.class)).isTrue();
    }

    @Test
    void dataCouchbaseTestPropagatesAutoConfigurationExclusions() {
        ImportAutoConfiguration annotation = MergedAnnotations.from(DataCouchbaseSlice.class)
                .get(ImportAutoConfiguration.class)
                .synthesize();

        assertThat(annotation.exclude()).containsExactly(
                CouchbaseAutoConfiguration.class,
                DataCouchbaseAutoConfiguration.class,
                DataCouchbaseReactiveAutoConfiguration.class,
                DataCouchbaseReactiveRepositoriesAutoConfiguration.class,
                DataCouchbaseRepositoriesAutoConfiguration.class);
    }

}

@DataCouchbaseTest(
        properties = "spring.profiles.active=data-couchbase-slice",
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = IncludedComponent.class),
        excludeAutoConfiguration = {
            CouchbaseAutoConfiguration.class,
            DataCouchbaseAutoConfiguration.class,
            DataCouchbaseReactiveAutoConfiguration.class,
            DataCouchbaseReactiveRepositoriesAutoConfiguration.class,
            DataCouchbaseRepositoriesAutoConfiguration.class
        })
class DataCouchbaseSlice {

}

class IncludedComponent {

}

@AutoConfigureDataCouchbase
class AutoConfiguredComponent {

}
