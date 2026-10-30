/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_persistence;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityScanTest {

    @Test
    void resolvesPropertyPlaceholdersAndPackageDelimiters() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "entity-packages", Map.of("entity.packages", "app.catalog, app.orders;app.customers")));
            context.register(PropertyPackages.class);
            context.refresh();

            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly("app.catalog", "app.orders", "app.customers");
        }
    }

    @Test
    void valueAliasRegistersExplicitPackages() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AliasedPackages.class)) {
            assertThat(EntityScanPackages.get(context).getPackageNames()).containsExactly("app.accounts", "app.audit");
        }
    }

    @Test
    void markerClassesSelectTheirPackageWithoutDuplicates() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MarkerPackages.class)) {
            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly(EntityScanTest.class.getPackageName());
        }
    }

    @Test
    void absentAttributesSelectTheConfigurationPackage() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(DefaultPackages.class)) {
            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly(EntityScanTest.class.getPackageName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackages = "${entity.packages}")
    public static class PropertyPackages {
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan({"app.accounts", "app.audit"})
    public static class AliasedPackages {
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {EntityScanTest.class, EntityScanPackagesTest.class})
    public static class MarkerPackages {
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan
    public static class DefaultPackages {
    }
}
