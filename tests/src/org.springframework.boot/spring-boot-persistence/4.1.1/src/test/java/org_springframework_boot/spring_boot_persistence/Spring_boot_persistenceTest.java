/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_persistence;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.stereotype.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_persistenceTest {

    @Test
    void entityScanPackagesRegistersAndMergesPackageNames() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        assertThat(EntityScanPackages.get(beanFactory).getPackageNames()).isEmpty();

        EntityScanPackages.register(beanFactory, "com.example.orders", "com.example.billing");
        EntityScanPackages.register(beanFactory, List.of("com.example.billing", "com.example.shipping"));

        assertThat(EntityScanPackages.get(beanFactory).getPackageNames())
                .containsExactly("com.example.orders", "com.example.billing", "com.example.shipping");
    }

    @Test
    void entityScanAnnotationRegistersPackages() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(EntityScanConfiguration.class);
            context.refresh();

            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly(ScannedEntityType.class.getPackageName());
        }
    }

    @Test
    void entityScanValueAliasRegistersPackages() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ValueEntityScanConfiguration.class);
            context.refresh();

            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly(ScannedEntityType.class.getPackageName());
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationTranslatesRepositoryExceptions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", Map.of("spring.aop.proxy-target-class", false)));
            context.register(PersistenceExceptionTranslationConfiguration.class);
            context.refresh();

            PersistenceExceptionTranslationPostProcessor postProcessor =
                    context.getBean(PersistenceExceptionTranslationPostProcessor.class);
            RepositoryOperations repository = context.getBean(RepositoryOperations.class);

            assertThat(postProcessor.isProxyTargetClass()).isFalse();
            assertThatThrownBy(repository::load)
                    .isInstanceOf(InvalidDataAccessApiUsageException.class)
                    .hasMessage("Translated")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void persistenceExceptionTranslationCanBeDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", Map.of("spring.persistence.exceptiontranslation.enabled", false)));
            context.register(PersistenceExceptionTranslationConfiguration.class);
            context.refresh();

            RepositoryOperations repository = context.getBean(RepositoryOperations.class);

            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class)).isEmpty();
            assertThatThrownBy(repository::load)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Database unavailable");
        }
    }

    @Test
    void persistenceExceptionTranslationBacksOffWhenPostProcessorIsProvided() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("userProvidedPersistenceExceptionTranslationPostProcessor",
                    PersistenceExceptionTranslationPostProcessor.class,
                    PersistenceExceptionTranslationPostProcessor::new);
            context.register(PersistenceExceptionTranslationAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class))
                    .containsOnlyKeys("userProvidedPersistenceExceptionTranslationPostProcessor");
        }
    }

    @EntityScan(basePackages = "org_springframework_boot.spring_boot_persistence",
            basePackageClasses = ScannedEntityType.class)
    @Configuration(proxyBeanMethods = false)
    static class EntityScanConfiguration {

    }

    @EntityScan("org_springframework_boot.spring_boot_persistence")
    @Configuration(proxyBeanMethods = false)
    static class ValueEntityScanConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    @Import(PersistenceExceptionTranslationAutoConfiguration.class)
    static class PersistenceExceptionTranslationConfiguration {

        @Bean
        PersistenceExceptionTranslator persistenceExceptionTranslator() {
            return new TestPersistenceExceptionTranslator();
        }

        @Bean
        RepositoryOperations repositoryOperations() {
            return new FailingRepositoryOperations();
        }

    }

    public interface RepositoryOperations {

        void load();

    }

    @Repository
    public static class FailingRepositoryOperations implements RepositoryOperations {

        @Override
        public void load() {
            throw new IllegalStateException("Database unavailable");
        }

    }

    static class TestPersistenceExceptionTranslator implements PersistenceExceptionTranslator {

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException exception) {
            return new InvalidDataAccessApiUsageException("Translated", exception);
        }

    }

    static class ScannedEntityType {

    }

}
