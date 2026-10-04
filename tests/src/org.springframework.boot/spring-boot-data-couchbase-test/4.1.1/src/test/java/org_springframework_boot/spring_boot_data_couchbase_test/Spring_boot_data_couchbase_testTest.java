/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseRepositoriesAutoConfiguration;
import org.springframework.boot.data.couchbase.test.autoconfigure.AutoConfigureDataCouchbase;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_couchbase_testTest {

    @Test
    void dataCouchbaseTestExposesSliceConfiguration() {
        DataCouchbaseTest annotation = MergedAnnotations.from(DataCouchbaseSliceConfiguration.class)
                .get(DataCouchbaseTest.class)
                .synthesize();

        assertThat(annotation.properties()).containsExactly("spring.data.couchbase.bucket-name=orders",
                "spring.data.couchbase.scope-name=fulfillment");
        assertThat(annotation.useDefaultFilters()).isFalse();
        assertThat(annotation.includeFilters()).hasSize(1);
        assertThat(annotation.includeFilters()[0].type()).isEqualTo(FilterType.ANNOTATION);
        assertThat(annotation.includeFilters()[0].classes()).containsExactly(Document.class);
        assertThat(annotation.excludeFilters()).hasSize(1);
        assertThat(annotation.excludeFilters()[0].type()).isEqualTo(FilterType.ASSIGNABLE_TYPE);
        assertThat(annotation.excludeFilters()[0].classes()).containsExactly(CouchbaseMappingContext.class);
        assertThat(annotation.excludeAutoConfiguration()).containsExactly(DataCouchbaseRepositoriesAutoConfiguration.class);
        assertThat(MergedAnnotations.from(DataCouchbaseSliceConfiguration.class)
                .isPresent(AutoConfigureDataCouchbase.class))
                .isTrue();
    }

    @Test
    void autoConfigureDataCouchbaseListsItsAutoConfigurations() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfigureDataCouchbase.class,
                getClass().getClassLoader());

        assertThat(candidates.getCandidates()).containsExactly(
                "org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration",
                "org.springframework.boot.couchbase.autoconfigure.CouchbaseAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveRepositoriesAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseRepositoriesAutoConfiguration",
                "optional:org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration");
    }

    @DataCouchbaseTest(properties = { "spring.data.couchbase.bucket-name=orders",
            "spring.data.couchbase.scope-name=fulfillment" }, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Document.class),
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = CouchbaseMappingContext.class),
            excludeAutoConfiguration = DataCouchbaseRepositoriesAutoConfiguration.class)
    @Configuration(proxyBeanMethods = false)
    static class DataCouchbaseSliceConfiguration {

    }

}
