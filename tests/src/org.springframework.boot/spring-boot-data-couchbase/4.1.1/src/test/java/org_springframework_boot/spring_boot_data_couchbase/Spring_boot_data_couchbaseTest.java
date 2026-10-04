/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseProperties;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseRepositoriesAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.convert.translation.TranslationService;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.mapping.model.SnakeCaseFieldNamingStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_couchbaseTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataCouchbaseAutoConfiguration.class));

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(DataCouchbaseAutoConfiguration.class.getName(),
                DataCouchbaseReactiveAutoConfiguration.class.getName(),
                DataCouchbaseRepositoriesAutoConfiguration.class.getName(),
                DataCouchbaseReactiveRepositoriesAutoConfiguration.class.getName());
    }

    @Test
    void autoConfigurationBindsPropertiesAndCreatesCouchbaseInfrastructure() {
        this.contextRunner
                .withPropertyValues("spring.data.couchbase.auto-index=true",
                        "spring.data.couchbase.bucket-name=orders",
                        "spring.data.couchbase.scope-name=fulfillment",
                        "spring.data.couchbase.field-naming-strategy="
                                + SnakeCaseFieldNamingStrategy.class.getName(),
                        "spring.data.couchbase.type-key=kind")
                .run((context) -> {
                    assertThat(context).hasSingleBean(DataCouchbaseProperties.class);
                    assertThat(context).hasSingleBean(CouchbaseCustomConversions.class);
                    assertThat(context).hasSingleBean(CouchbaseMappingContext.class);
                    assertThat(context).hasSingleBean(MappingCouchbaseConverter.class);
                    assertThat(context).hasSingleBean(TranslationService.class);

                    DataCouchbaseProperties properties = context.getBean(DataCouchbaseProperties.class);
                    assertThat(properties.isAutoIndex()).isTrue();
                    assertThat(properties.getBucketName()).isEqualTo("orders");
                    assertThat(properties.getScopeName()).isEqualTo("fulfillment");
                    assertThat(properties.getFieldNamingStrategy()).isEqualTo(SnakeCaseFieldNamingStrategy.class);
                    assertThat(properties.getTypeKey()).isEqualTo("kind");
                });
    }

    @Test
    void autoConfigurationAppliesMappingAndConversionSettings() {
        this.contextRunner
                .withPropertyValues("spring.data.couchbase.auto-index=true",
                        "spring.data.couchbase.type-key=documentType")
                .run((context) -> {
                    CouchbaseMappingContext mappingContext = context.getBean(CouchbaseMappingContext.class);
                    MappingCouchbaseConverter converter = context.getBean(MappingCouchbaseConverter.class);

                    assertThat(mappingContext.isAutoIndexCreation()).isTrue();
                    assertThat(converter.getTypeKey()).isEqualTo("documentType");
                });
    }

    @Test
    void autoConfigurationUsesApplicationCustomConversions() {
        this.contextRunner.withUserConfiguration(CustomConversionsConfiguration.class).run((context) -> {
            MappingCouchbaseConverter converter = context.getBean(MappingCouchbaseConverter.class);
            CouchbaseDocument document = new CouchbaseDocument();

            converter.write(new CustomConvertedDocument("order-1", new Price(42)), document);

            assertThat(document.get("price")).isEqualTo("price-42");
        });
    }

    @Test
    void connectionDependentAutoConfigurationsRemainInactiveWithoutClientFactory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataCouchbaseAutoConfiguration.class,
                        DataCouchbaseReactiveAutoConfiguration.class,
                        DataCouchbaseRepositoriesAutoConfiguration.class,
                        DataCouchbaseReactiveRepositoriesAutoConfiguration.class))
                .run((context) -> {
                    assertThat(context).hasSingleBean(CouchbaseMappingContext.class);
                    assertThat(context).hasSingleBean(MappingCouchbaseConverter.class);
                    assertThat(context).doesNotHaveBean(CouchbaseClientFactory.class);
                    assertThat(context).doesNotHaveBean(CouchbaseRepository.class);
                });
    }

    @Test
    void autoConfigurationDiscoversCouchbaseDocumentsFromEntityScan() {
        this.contextRunner.withUserConfiguration(EntityScanConfiguration.class).run((context) -> {
            CouchbaseMappingContext mappingContext = context.getBean(CouchbaseMappingContext.class);

            assertThat(mappingContext.getPersistentEntity(CouchbaseTestDocument.class)).isNotNull();
        });
    }

    @Test
    void propertiesExposeDefaultsAndSupportProgrammaticConfiguration() {
        DataCouchbaseProperties properties = new DataCouchbaseProperties();

        assertThat(properties.isAutoIndex()).isFalse();
        assertThat(properties.getBucketName()).isNull();
        assertThat(properties.getScopeName()).isNull();
        assertThat(properties.getFieldNamingStrategy()).isNull();
        assertThat(properties.getTypeKey()).isEqualTo("_class");

        properties.setAutoIndex(true);
        properties.setBucketName("catalog");
        properties.setScopeName("products");
        properties.setFieldNamingStrategy(SnakeCaseFieldNamingStrategy.class);
        properties.setTypeKey("documentType");

        assertThat(properties.isAutoIndex()).isTrue();
        assertThat(properties.getBucketName()).isEqualTo("catalog");
        assertThat(properties.getScopeName()).isEqualTo("products");
        assertThat(properties.getFieldNamingStrategy()).isEqualTo(SnakeCaseFieldNamingStrategy.class);
        assertThat(properties.getTypeKey()).isEqualTo("documentType");
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = CouchbaseTestDocument.class)
    static class EntityScanConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = CustomConvertedDocument.class)
    static class CustomConversionsConfiguration {

        @Bean
        CouchbaseCustomConversions couchbaseCustomConversions() {
            return CouchbaseCustomConversions.create(
                    (adapter) -> adapter.registerConverter(new PriceToStringConverter()));
        }

    }

    @Document
    static class CouchbaseTestDocument {

    }

    @Document
    static class CustomConvertedDocument {

        @Id
        private String id;

        private Price price;

        CustomConvertedDocument(String id, Price price) {
            this.id = id;
            this.price = price;
        }

    }

    static class Price {

        private final int amount;

        Price(int amount) {
            this.amount = amount;
        }

    }

    static class PriceToStringConverter implements Converter<Price, String> {

        @Override
        public String convert(Price source) {
            return "price-" + source.amount;
        }

    }

}
