/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseAutoConfiguration;
import org.springframework.boot.data.couchbase.test.autoconfigure.AutoConfigureDataCouchbase;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_couchbase_testTest {

    @Test
    void autoConfigureDataCouchbaseProvidesCouchbaseInfrastructure() {
        new ApplicationContextRunner()
                .withUserConfiguration(AutoConfigureDataCouchbaseConfiguration.class)
                .run((context) -> {
                    assertThat(context.getBeansOfType(CouchbaseMappingContext.class)).hasSize(1);
                    assertThat(context.getBeansOfType(MappingCouchbaseConverter.class)).hasSize(1);
                });
    }

    @Test
    void dataCouchbaseTestBootstrapsSliceWithPropertiesAndFilters() throws Exception {
        TestContextManager testContextManager = new TestContextManager(DataCouchbaseSliceScenario.class);
        testContextManager.beforeTestClass();
        try {
            ApplicationContext context = testContextManager.getTestContext().getApplicationContext();

            assertThat(context.getEnvironment().getProperty("spring.data.couchbase.type-key"))
                    .isEqualTo("documentKind");
            assertThat(context.getBeansOfType(CouchbaseMappingContext.class)).hasSize(1);
            assertThat(context.getBeansOfType(MappingCouchbaseConverter.class)).hasSize(1);
            assertThat(context.getBean(MappingCouchbaseConverter.class).getTypeKey()).isEqualTo("documentKind");
            assertThat(context.getBeansOfType(IncludedCouchbaseComponent.class)).hasSize(1);
            assertThat(context.getBeansOfType(ExcludedCouchbaseComponent.class)).isEmpty();
        }
        finally {
            testContextManager.afterTestClass();
        }
    }

    @Test
    void dataCouchbaseTestAppliesExcludeFilters() throws Exception {
        TestContextManager testContextManager = new TestContextManager(DataCouchbaseSliceWithExcludeFilterScenario.class);
        testContextManager.beforeTestClass();
        try {
            ApplicationContext context = testContextManager.getTestContext().getApplicationContext();

            assertThat(context.getBeansOfType(IncludedCouchbaseComponent.class)).hasSize(1);
            assertThat(context.getBeansOfType(ExcludedCouchbaseComponent.class)).isEmpty();
        }
        finally {
            testContextManager.afterTestClass();
        }
    }

    @Test
    void dataCouchbaseTestUsesDefaultComponentFilters() throws Exception {
        TestContextManager testContextManager = new TestContextManager(DataCouchbaseSliceWithDefaultFiltersScenario.class);
        testContextManager.beforeTestClass();
        try {
            ApplicationContext context = testContextManager.getTestContext().getApplicationContext();

            assertThat(context.getBeansOfType(IncludedCouchbaseComponent.class)).isEmpty();
            assertThat(context.getBeansOfType(ExcludedCouchbaseComponent.class)).isEmpty();
        }
        finally {
            testContextManager.afterTestClass();
        }
    }

    @Test
    void dataCouchbaseTestCanExcludeItsAutoConfiguration() throws Exception {
        TestContextManager testContextManager = new TestContextManager(DataCouchbaseTestWithoutAutoConfiguration.class);
        testContextManager.beforeTestClass();
        try {
            ApplicationContext context = testContextManager.getTestContext().getApplicationContext();

            assertThat(context.getBeansOfType(CouchbaseMappingContext.class)).isEmpty();
            assertThat(context.getBeansOfType(MappingCouchbaseConverter.class)).isEmpty();
        }
        finally {
            testContextManager.afterTestClass();
        }
    }

}

@AutoConfigureDataCouchbase
@Configuration(proxyBeanMethods = false)
class AutoConfigureDataCouchbaseConfiguration {

}

@DataCouchbaseTest(properties = "spring.data.couchbase.type-key=documentKind", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = IncludedCouchbaseComponent.class))
@ContextConfiguration(classes = DataCouchbaseSliceConfiguration.class)
class DataCouchbaseSliceScenario {

}

@DataCouchbaseTest(useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = { IncludedCouchbaseComponent.class, ExcludedCouchbaseComponent.class }),
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = ExcludedCouchbaseComponent.class))
@ContextConfiguration(classes = DataCouchbaseSliceConfiguration.class)
class DataCouchbaseSliceWithExcludeFilterScenario {

}

@DataCouchbaseTest
@ContextConfiguration(classes = DataCouchbaseSliceConfiguration.class)
class DataCouchbaseSliceWithDefaultFiltersScenario {

}

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = IncludedCouchbaseComponent.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
class DataCouchbaseSliceConfiguration {

}

@Configuration(proxyBeanMethods = false)
class DataCouchbaseTestWithoutAutoConfigurationConfiguration {

}

@DataCouchbaseTest(excludeAutoConfiguration = DataCouchbaseAutoConfiguration.class)
@ContextConfiguration(classes = DataCouchbaseTestWithoutAutoConfigurationConfiguration.class)
class DataCouchbaseTestWithoutAutoConfiguration {

}

@Component
class IncludedCouchbaseComponent {

}

@Component
class ExcludedCouchbaseComponent {

}
