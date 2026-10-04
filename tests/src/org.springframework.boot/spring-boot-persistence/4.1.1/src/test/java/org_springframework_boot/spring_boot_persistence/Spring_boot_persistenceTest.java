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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScanner;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.InvalidDataAccessApiUsageException;
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
    void entityScanAnnotationRegistersPackagesForEntityScanner() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(EntityScanConfiguration.class);
            context.refresh();

            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly(ScannedEntityType.class.getPackageName());

            Set<Class<?>> scannedTypes = new EntityScanner(context).scan(ScannedEntity.class);

            assertThat(scannedTypes).containsExactly(ScannedEntityType.class);
        }
    }

    @Test
    void entityScannerUsesAutoConfigurationPackagesWhenEntityScanPackagesAreAbsent()
            throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            AutoConfigurationPackages.register(context, ScannedEntityType.class.getPackageName());
            context.refresh();

            Set<Class<?>> scannedTypes = new EntityScanner(context).scan(ScannedEntity.class);

            assertThat(scannedTypes).containsExactly(ScannedEntityType.class);
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationTranslatesRepositoryExceptions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PersistenceExceptionTranslationConfiguration.class)) {
            RepositoryOperations repository = context.getBean(RepositoryOperations.class);

            assertThatThrownBy(repository::load)
                    .isInstanceOf(InvalidDataAccessApiUsageException.class)
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

            assertThatThrownBy(repository::load)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Database unavailable");
        }
    }

    @EntityScan(basePackages = "org_springframework_boot.spring_boot_persistence",
            basePackageClasses = ScannedEntityType.class)
    @Configuration
    static class EntityScanConfiguration {

    }

    @Configuration
    @Import(PersistenceExceptionTranslationAutoConfiguration.class)
    static class PersistenceExceptionTranslationConfiguration {

        @Bean
        PersistenceExceptionTranslator persistenceExceptionTranslator() {
            return exception -> new InvalidDataAccessApiUsageException("Translated", exception);
        }

        @Bean
        RepositoryOperations repositoryOperations() {
            return new RepositoryOperations();
        }

    }

    @Repository
    public static class RepositoryOperations {

        public void load() {
            throw new IllegalStateException("Database unavailable");
        }

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ScannedEntity {

    }

    @ScannedEntity
    static class ScannedEntityType {

    }

}
