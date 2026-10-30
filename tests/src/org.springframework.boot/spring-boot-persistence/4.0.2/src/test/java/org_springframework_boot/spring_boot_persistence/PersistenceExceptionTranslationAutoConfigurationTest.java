/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_persistence;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.stereotype.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PersistenceExceptionTranslationAutoConfigurationTest {

    @Test
    void enablesClassBasedProxyConfigurationByDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PersistenceExceptionTranslationAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class)).hasSize(1);
            assertThat(context.getBean(PersistenceExceptionTranslationPostProcessor.class).isProxyTargetClass()).isTrue();
        }
    }

    @Test
    void interfaceBasedRepositoryTranslatesPersistenceExceptions() {
        try (AnnotationConfigApplicationContext context = repositoryContext(Map.of("spring.aop.proxy-target-class", false))) {
            context.refresh();
            Catalog catalog = context.getBean(Catalog.class);

            assertThat(context.getBean(PersistenceExceptionTranslationPostProcessor.class).isProxyTargetClass()).isFalse();
            assertThat(catalog.description()).isEqualTo("catalog");
            assertThatThrownBy(catalog::lookup)
                    .isInstanceOf(InvalidDataAccessResourceUsageException.class)
                    .hasMessage("Cannot read catalog")
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void disablingTranslationLeavesTheOriginalRepositoryException() {
        try (AnnotationConfigApplicationContext context = repositoryContext(
                Map.of("spring.persistence.exceptiontranslation.enabled", false))) {
            context.refresh();
            Catalog catalog = context.getBean(Catalog.class);

            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class)).isEmpty();
            assertThat(catalog.description()).isEqualTo("catalog");
            assertThatThrownBy(catalog::lookup).isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown record");
        }
    }

    @Test
    void existingPostProcessorPreventsASecondRegistration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            PersistenceExceptionTranslationPostProcessor custom = new PersistenceExceptionTranslationPostProcessor();
            custom.setProxyTargetClass(false);
            context.registerBean("customTranslation", PersistenceExceptionTranslationPostProcessor.class, () -> custom);
            context.register(PersistenceExceptionTranslationAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class))
                    .containsOnlyKeys("customTranslation");
            assertThat(context.getBean(PersistenceExceptionTranslationPostProcessor.class)).isSameAs(custom);
        }
    }

    private static AnnotationConfigApplicationContext repositoryContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("persistence-settings", properties));
        context.register(PersistenceExceptionTranslationAutoConfiguration.class);
        context.registerBean("catalog", UnavailableCatalog.class, UnavailableCatalog::new);
        context.registerBean("catalogTranslator", PersistenceExceptionTranslator.class,
                () -> exception -> exception instanceof IllegalArgumentException
                        ? new InvalidDataAccessResourceUsageException("Cannot read catalog", exception) : null);
        return context;
    }

    public interface Catalog {
        String description();

        String lookup();
    }

    @Repository
    public static class UnavailableCatalog implements Catalog {
        @Override
        public String description() {
            return "catalog";
        }

        @Override
        public String lookup() {
            throw new IllegalArgumentException("Unknown record");
        }
    }
}
