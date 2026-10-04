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
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityScannerTest {

    @Test
    void scansPackagesRegisteredByEntityScan() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(EntityScanConfiguration.class)) {
            Set<Class<?>> entities = new EntityScanner(context).scan(ScannedEntity.class);

            assertThat(entities).containsExactly(ScannedEntityType.class);
        }
    }

    @Test
    void scansAutoConfigurationPackagesWhenEntityScanPackagesAreAbsent() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            AutoConfigurationPackages.register(context, ScannedEntityType.class.getPackageName());
            context.refresh();

            Set<Class<?>> entities = new EntityScanner(context).scan(ScannedEntity.class);

            assertThat(entities).containsExactly(ScannedEntityType.class);
        }
    }

    @Test
    void scansConfigurationPackageWhenEntityScanHasNoExplicitPackages() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DefaultEntityScanConfiguration.class)) {
            Set<Class<?>> entities = new EntityScanner(context).scan(ScannedEntity.class);

            assertThat(entities).containsExactly(ScannedEntityType.class);
        }
    }

    @Test
    void returnsNoEntitiesWhenNoScanPackagesAreConfigured() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            Set<Class<?>> entities = new EntityScanner(context).scan(ScannedEntity.class);

            assertThat(entities).isEmpty();
        }
    }

    @Test
    void scansTypesMatchingAnyRequestedAnnotation() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(EntityScanConfiguration.class)) {
            Set<Class<?>> entities = new EntityScanner(context)
                    .scan(ScannedEntity.class, AlternateScannedEntity.class);

            assertThat(entities).containsExactlyInAnyOrder(ScannedEntityType.class,
                    AlternateScannedEntityType.class);
        }
    }

    @EntityScan(basePackageClasses = ScannedEntityType.class)
    @Configuration(proxyBeanMethods = false)
    static class EntityScanConfiguration {

    }

    @EntityScan
    @Configuration(proxyBeanMethods = false)
    static class DefaultEntityScanConfiguration {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ScannedEntity {

    }

    @ScannedEntity
    static class ScannedEntityType {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface AlternateScannedEntity {

    }

    @AlternateScannedEntity
    static class AlternateScannedEntityType {

    }

}
