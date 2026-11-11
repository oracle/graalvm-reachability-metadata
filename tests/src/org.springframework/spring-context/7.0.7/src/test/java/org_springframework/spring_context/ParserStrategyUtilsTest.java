/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserStrategyUtilsTest {
    @Test
    void injectsEnvironmentIntoImportSelectorConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("catalog", Map.of("forge.catalog.enabled", "true")));
            context.register(RootConfiguration.class);
            context.refresh();
            assertEquals("selected", context.getBean("selectedCatalog"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CatalogSelector.class)
    public static class RootConfiguration {
    }

    public static class CatalogSelector implements ImportSelector {
        private final Environment environment;

        public CatalogSelector(Environment environment) {
            this.environment = environment;
        }

        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            return environment.getProperty("forge.catalog.enabled", Boolean.class, false)
                    ? new String[]{SelectedConfiguration.class.getName()} : new String[0];
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SelectedConfiguration {
        @Bean
        public String selectedCatalog() {
            return "selected";
        }
    }
}
