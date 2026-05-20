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
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_persistenceTest {

    private static final String SCANNED_PACKAGE = ScannedEntity.class.getPackageName();

    @Test
    void entityScanPackagesCanBeRegisteredAndExtendedProgrammatically() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        assertThat(EntityScanPackages.get(beanFactory).getPackageNames()).isEmpty();

        EntityScanPackages.register(beanFactory, List.of("example.alpha", "", " ", "example.beta"));
        EntityScanPackages.register(beanFactory, "example.gamma", "example.alpha");

        assertThat(EntityScanPackages.get(beanFactory).getPackageNames())
                .containsExactly("example.alpha", "example.beta", "example.gamma");
    }

    @Test
    void entityScanAnnotationRegistersPlaceholderTokensAndMarkerClassPackages() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("entity-scan-test",
                    Map.of("entity.scan.packages", SCANNED_PACKAGE + ", example.one; example.two")));
            context.register(PlaceholderEntityScanConfiguration.class, MarkerEntityScanConfiguration.class);

            context.refresh();

            assertThat(EntityScanPackages.get(context).getPackageNames())
                    .containsExactly(SCANNED_PACKAGE, "example.one", "example.two");
        }
    }

    @Test
    void entityScanAnnotationUsesConfigurationPackageWhenNoPackagesAreDeclared() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                DefaultPackageEntityScanConfiguration.class)) {
            assertThat(EntityScanPackages.get(context).getPackageNames()).containsExactly(SCANNED_PACKAGE);
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationCreatesPostProcessorByDefault() {
        try (AnnotationConfigApplicationContext context = autoConfigurationContext(Map.of())) {
            PersistenceExceptionTranslationPostProcessor postProcessor = context
                    .getBean(PersistenceExceptionTranslationPostProcessor.class);

            assertThat(postProcessor.isProxyTargetClass()).isTrue();
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationHonorsProxyTargetClassProperty() {
        try (AnnotationConfigApplicationContext context = autoConfigurationContext(
                Map.of("spring.aop.proxy-target-class", "false"))) {
            PersistenceExceptionTranslationPostProcessor postProcessor = context
                    .getBean(PersistenceExceptionTranslationPostProcessor.class);

            assertThat(postProcessor.isProxyTargetClass()).isFalse();
        }
    }

    @Test
    void persistenceExceptionTranslationAutoConfigurationCanBeDisabled() {
        try (AnnotationConfigApplicationContext context = autoConfigurationContext(
                Map.of("spring.persistence.exceptiontranslation.enabled", "false"))) {
            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class)).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext autoConfigurationContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("persistence-exception-translation-test", properties));
        context.register(PersistenceExceptionTranslationAutoConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan("${entity.scan.packages}")
    static class PlaceholderEntityScanConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ScannedEntity.class)
    static class MarkerEntityScanConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan
    static class DefaultPackageEntityScanConfiguration {
    }

    static class ScannedEntity {
    }
}
