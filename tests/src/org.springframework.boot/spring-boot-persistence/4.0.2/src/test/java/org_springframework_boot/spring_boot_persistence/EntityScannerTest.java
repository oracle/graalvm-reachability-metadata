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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScanner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Indexed;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityScannerTest {

    @Test
    void discoversConcreteAnnotatedEntitiesFromEntityScan() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EntityConfiguration.class)) {
            EntityScanner scanner = new EntityScanner(context);

            assertThat(scanner.scan(StoredRecord.class)).containsExactly(CatalogRecord.class);
            assertThat(scanner.scan(StoredRecord.class, StoredEvent.class))
                    .containsExactlyInAnyOrder(CatalogRecord.class, OrderEvent.class);
        }
    }

    @Test
    void usesAutoConfigurationPackagesWhenNoEntityPackagesAreRegistered() throws ClassNotFoundException {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            AutoConfigurationPackages.register(context, EntityScannerTest.class.getPackageName());
            context.refresh();

            assertThat(new EntityScanner(context).scan(StoredEvent.class)).containsExactly(OrderEvent.class);
        }
    }

    @Test
    void explicitEntityPackagesTakePrecedenceOverAutoConfigurationPackages() throws ClassNotFoundException {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            AutoConfigurationPackages.register(context, "app.no_entities");
            EntityScanPackages.register(context, EntityScannerTest.class.getPackageName());
            context.refresh();

            assertThat(new EntityScanner(context).scan(StoredRecord.class)).containsExactly(CatalogRecord.class);
        }
    }

    @Test
    void scanningWithoutRegisteredPackagesReturnsNoEntities() throws ClassNotFoundException {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();

            assertThat(new EntityScanner(context).scan(StoredRecord.class)).isEmpty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = CatalogRecord.class)
    public static class EntityConfiguration {
    }

    @Indexed
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface StoredRecord {
    }

    @Indexed
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface StoredEvent {
    }

    @StoredRecord
    public static class CatalogRecord {
    }

    @StoredRecord
    public abstract static class AbstractRecord {
    }

    @StoredEvent
    public static class OrderEvent {
    }
}
