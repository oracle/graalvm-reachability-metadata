/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import java.util.List;

import com.couchbase.client.java.Cluster;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseProperties;
import org.springframework.boot.data.couchbase.test.autoconfigure.AutoConfigureDataCouchbase;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleRegistry;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;

import static org.assertj.core.api.Assertions.assertThat;

/// Applies the auto-configuration imports of the Data Couchbase slice to a plain application context.
public class AutoConfigureDataCouchbaseTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.data.couchbase.bucket-name=catalog");

    @Test
    void annotationPublishesTheDataCouchbaseAutoConfigurationImports() {
        List<String> candidates = ImportCandidates
                .load(AutoConfigureDataCouchbase.class, AutoConfigureDataCouchbaseTest.class.getClassLoader())
                .getCandidates();

        assertThat(candidates).containsExactly(
                "org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration",
                "org.springframework.boot.couchbase.autoconfigure.CouchbaseAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveRepositoriesAutoConfiguration",
                "org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseRepositoriesAutoConfiguration",
                "optional:org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration");
    }

    @Test
    void importsDataPropertiesAndSslServicesWithoutOpeningADatabaseConnection() {
        this.contextRunner.withUserConfiguration(CouchbaseConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataCouchbaseProperties.class).getBucketName()).isEqualTo("catalog");
            SslBundle bundle = SslBundle.of(SslStoreBundle.of(null, null, null));
            context.getBean(SslBundleRegistry.class).registerBundle("catalog", bundle);
            assertThat(context.getBean(SslBundles.class).getBundle("catalog")).isSameAs(bundle);
            assertThat(context).hasSingleBean(MappingCouchbaseConverter.class);
            assertThat(context).doesNotHaveBean(Cluster.class);
            assertThat(context).doesNotHaveBean(CouchbaseClientFactory.class);
        });
    }

    @Test
    void honorsConsumerAutoConfigurationExclusions() {
        this.contextRunner.withUserConfiguration(WithoutSslConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("sslBundleRegistry");
            assertThat(context).doesNotHaveBean(SslBundles.class);
            assertThat(context).hasSingleBean(DataCouchbaseProperties.class);
        });
    }

    @Test
    void honorsTheSliceAutoConfigurationExclusions() {
        this.contextRunner.withUserConfiguration(SliceWithoutDataCouchbase.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DataCouchbaseProperties.class);
            assertThat(context).doesNotHaveBean(MappingCouchbaseConverter.class);
            assertThat(context).hasSingleBean(SslBundles.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureDataCouchbase
    public static class CouchbaseConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureDataCouchbase
    @ImportAutoConfiguration(exclude = SslAutoConfiguration.class)
    public static class WithoutSslConfiguration {
    }

    /// Registered the way the TestContext framework imports a slice test class: as configuration.
    @Configuration(proxyBeanMethods = false)
    @DataCouchbaseTest(excludeAutoConfiguration = DataCouchbaseAutoConfiguration.class)
    public static class SliceWithoutDataCouchbase {
    }
}
