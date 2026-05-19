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

import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScanner;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class SpringBootPersistenceTest {

    @Test
    void entityScanPackagesGetReturnsEmptyPackagesWhenNoneAreRegistered() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            EntityScanPackages packages = EntityScanPackages.get(context);

            assertThat(packages.getPackageNames()).isEmpty();
        }
    }

    @Test
    void entityScanPackagesRegisterMergesPackagesAndFiltersBlankEntries() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            EntityScanPackages.register(context, "java.lang", "", " ");
            EntityScanPackages.register(context, List.of("java.util", "java.lang"));
            context.refresh();

            List<String> packageNames = EntityScanPackages.get(context).getPackageNames();

            assertThat(packageNames).containsExactly("java.lang", "java.util");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> packageNames.add("java.time"));
        }
    }

    @Test
    void entityScanAnnotationRegistersResolvedPackages() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("scan.base", "java.time")));
            context.register(EntityScanConfiguration.class);
            context.refresh();

            List<String> packageNames = EntityScanPackages.get(context).getPackageNames();

            assertThat(packageNames).containsExactly("java.time", "java.lang", "java.util",
                    PackageMarker.class.getPackageName());
        }
    }

    @Test
    void entityScanAnnotationUsesConfigurationPackageWhenNoBasePackageIsSpecified() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(DefaultEntityScanConfiguration.class);
            context.refresh();

            List<String> packageNames = EntityScanPackages.get(context).getPackageNames();

            assertThat(packageNames).containsExactly(SpringBootPersistenceTest.class.getPackageName());
        }
    }

    @Test
    void entityScannerFindsAnnotatedTypesFromExplicitEntityScanPackages() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            EntityScanPackages.register(context, SampleAccount.class.getPackageName());
            context.refresh();

            Set<Class<?>> scannedTypes = new EntityScanner(context).scan(SamplePersistentType.class);

            assertThat(scannedTypes).containsExactlyInAnyOrder(SampleAccount.class, SampleOrder.class);
        }
    }

    @Test
    void entityScannerFallsBackToAutoConfigurationPackages() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            AutoConfigurationPackages.register(context, SampleAccount.class.getPackageName());
            context.refresh();

            Set<Class<?>> scannedTypes = new EntityScanner(context).scan(SamplePersistentType.class);

            assertThat(scannedTypes).containsExactlyInAnyOrder(SampleAccount.class, SampleOrder.class);
        }
    }

    @Test
    void entityScannerFindsAnnotatedTypesForEachAnnotationFilter() throws ClassNotFoundException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            EntityScanPackages.register(context, SampleAccount.class.getPackageName());
            context.refresh();

            Set<Class<?>> scannedTypes = new EntityScanner(context).scan(SamplePersistentType.class,
                    SampleAuditedPersistentType.class);

            assertThat(scannedTypes).containsExactlyInAnyOrder(SampleAccount.class, SampleOrder.class,
                    SampleInvoice.class);
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationCreatesPostProcessorWhenEnabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("spring.aop.proxy-target-class", Boolean.TRUE.toString())));
            context.register(PersistenceExceptionTranslationAutoConfiguration.class);
            context.refresh();

            PersistenceExceptionTranslationPostProcessor postProcessor = context
                    .getBean(PersistenceExceptionTranslationPostProcessor.class);

            assertThat(postProcessor.isProxyTargetClass()).isTrue();
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationAppliesProxyTargetClassProperty() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("spring.aop.proxy-target-class", Boolean.FALSE.toString())));
            context.register(PersistenceExceptionTranslationAutoConfiguration.class);
            context.refresh();

            PersistenceExceptionTranslationPostProcessor postProcessor = context
                    .getBean(PersistenceExceptionTranslationPostProcessor.class);

            assertThat(postProcessor.isProxyTargetClass()).isFalse();
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationCanBeDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test",
                            Map.of("spring.persistence.exceptiontranslation.enabled", Boolean.FALSE.toString())));
            context.register(PersistenceExceptionTranslationAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class)).isEmpty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackages = { "${scan.base}", "java.lang, java.util" }, basePackageClasses = PackageMarker.class)
    static class EntityScanConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan
    static class DefaultEntityScanConfiguration {

    }

}

final class PackageMarker {

}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface SamplePersistentType {

}

@SamplePersistentType
final class SampleAccount {

}

@SamplePersistentType
final class SampleOrder {

}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface SampleAuditedPersistentType {

}

@SampleAuditedPersistentType
final class SampleInvoice {

}
