/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScanner;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.stereotype.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_persistenceTest {

    @Test
    void autoConfigurationIsPublishedAsImportCandidate() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class,
                PersistenceExceptionTranslationAutoConfiguration.class.getClassLoader());

        assertThat(candidates.getCandidates())
                .contains(PersistenceExceptionTranslationAutoConfiguration.class.getName());
    }

    @Test
    void entityScanRegistersDefaultPackageWhenNoExplicitPackageIsConfigured() {
        try (AnnotationConfigApplicationContext context = contextWith(DefaultEntityScanConfiguration.class)) {
            EntityScanPackages packages = EntityScanPackages.get(context);

            assertThat(packages.getPackageNames()).containsExactly(Spring_boot_persistenceTest.class.getPackageName());
        }
    }

    @Test
    void entityScanRegistersExplicitPackagesWithPlaceholdersAndMarkerClasses() {
        Map<String, Object> properties = Map.of("entity.packages",
                "com.example.account, com.example.audit;com.example.reporting");

        try (AnnotationConfigApplicationContext context = contextWithProperties(properties,
                ExplicitEntityScanConfiguration.class)) {
            EntityScanPackages packages = EntityScanPackages.get(context);

            assertThat(packages.getPackageNames()).containsExactly("com.example.account", "com.example.audit",
                    "com.example.reporting", EntityPackageMarker.class.getPackageName());
        }
    }

    @Test
    void entityScanPackagesRegisterMergesPackageNamesAndIgnoresBlankEntries() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

        EntityScanPackages.register(registry, "com.example.alpha", " ", "com.example.beta");
        EntityScanPackages.register(registry, List.of("com.example.beta", "", "com.example.gamma"));

        assertThat(EntityScanPackages.get(registry).getPackageNames()).containsExactly("com.example.alpha",
                "com.example.beta", "com.example.gamma");
        assertThat(EntityScanPackages.get(new DefaultListableBeanFactory()).getPackageNames()).isEmpty();
    }

    @Test
    void entityScannerFindsAnnotatedEntityTypesFromEntityScanPackages() throws Exception {
        try (AnnotationConfigApplicationContext context = contextWith(ScanningEntityScanConfiguration.class)) {
            EntityScanner scanner = new EntityScanner(context);

            Set<Class<?>> entities = scanner.scan(SampleEntity.class, SampleEmbeddable.class);

            assertThat(entities).contains(ScannedEntity.class, ScannedEmbeddable.class)
                    .doesNotContain(UnannotatedPersistenceType.class);
        }
    }

    @Test
    void entityScannerFallsBackToAutoConfigurationPackagesWhenEntityScanPackagesAreAbsent() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            AutoConfigurationPackages.register(context, Spring_boot_persistenceTest.class.getPackageName());
            context.refresh();

            Set<Class<?>> entities = new EntityScanner(context).scan(SampleEntity.class);

            assertThat(EntityScanPackages.get(context).getPackageNames()).isEmpty();
            assertThat(entities).contains(ScannedEntity.class)
                    .doesNotContain(ScannedEmbeddable.class, UnannotatedPersistenceType.class);
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationCreatesTranslatingPostProcessor() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.aop.proxy-target-class", "false"),
                PersistenceExceptionTranslationAutoConfiguration.class, RepositoryConfiguration.class)) {
            SampleRepository repository = context.getBean(SampleRepository.class);

            assertThat(context.getBean(PersistenceExceptionTranslationPostProcessor.class)).isNotNull();
            assertThat(AopUtils.isJdkDynamicProxy(repository)).isTrue();
            assertThatThrownBy(repository::findName)
                    .isInstanceOf(DataAccessResourceFailureException.class)
                    .hasMessageContaining("translated persistence failure")
                    .cause()
                    .isInstanceOf(TestPersistenceException.class);
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationCanBeDisabled() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.persistence.exceptiontranslation.enabled", "false"),
                PersistenceExceptionTranslationAutoConfiguration.class, RepositoryConfiguration.class)) {
            SampleRepository repository = context.getBean(SampleRepository.class);

            assertThat(context.getBeanNamesForType(PersistenceExceptionTranslationPostProcessor.class)).isEmpty();
            assertThat(AopUtils.isAopProxy(repository)).isFalse();
            assertThatThrownBy(repository::findName).isInstanceOf(TestPersistenceException.class);
        }
    }

    private static AnnotationConfigApplicationContext contextWith(Class<?>... configurationClasses) {
        return contextWithProperties(Map.of(), configurationClasses);
    }

    private static AnnotationConfigApplicationContext contextWithProperties(Map<String, Object> properties,
            Class<?>... configurationClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        context.register(configurationClasses);
        try {
            context.refresh();
        } catch (RuntimeException ex) {
            context.close();
            throw ex;
        }
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan
    static class DefaultEntityScanConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackages = "${entity.packages}", basePackageClasses = EntityPackageMarker.class)
    static class ExplicitEntityScanConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Spring_boot_persistenceTest.class)
    static class ScanningEntityScanConfiguration {

    }

    public static final class EntityPackageMarker {

    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleEntity {

    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleEmbeddable {

    }

    @SampleEntity
    public static final class ScannedEntity {

    }

    @SampleEmbeddable
    public static final class ScannedEmbeddable {

    }

    public static final class UnannotatedPersistenceType {

    }

    @Configuration(proxyBeanMethods = false)
    static class RepositoryConfiguration {

        @Bean
        SampleRepository sampleRepository() {
            return new FailingSampleRepository();
        }

        @Bean
        PersistenceExceptionTranslator persistenceExceptionTranslator() {
            return (ex) -> {
                if (ex instanceof TestPersistenceException) {
                    return new DataAccessResourceFailureException("translated persistence failure", ex);
                }
                return null;
            };
        }

    }

    public interface SampleRepository {

        String findName();

    }

    @Repository
    public static final class FailingSampleRepository implements SampleRepository {

        @Override
        public String findName() {
            throw new TestPersistenceException();
        }

    }

    public static final class TestPersistenceException extends RuntimeException {

    }

}
