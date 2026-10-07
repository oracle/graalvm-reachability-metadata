/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.annotation.DeterminableImports;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.test.context.TestContextManager;

public class ImportsContextCustomizerInnerContextCustomizerKeyTest {
    @Test
    void instantiatesDeterminableImportAndAppliesItsConfiguration() {
        TestContextManager manager = new TestContextManager(ImportFixture.class);
        try (ConfigurableApplicationContext context =
                (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext()) {
            assertThat(DeterminableConfiguration.importsDetermined).isTrue();
            assertThat(context.getBean("importedMessage", String.class)).isEqualTo("imported");
        }
    }

    @SpringBootTest(classes = RootConfiguration.class)
    @Import(DeterminableConfiguration.class)
    static class ImportFixture {}

    @Configuration(proxyBeanMethods = false)
    static class RootConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @Import(ImportedConfiguration.class)
    public static class DeterminableConfiguration implements DeterminableImports {
        static boolean importsDetermined;

        @Override
        public Set<Object> determineImports(AnnotationMetadata metadata) {
            importsDetermined = true;
            return Set.of(ImportedConfiguration.class.getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ImportedConfiguration {
        @Bean
        String importedMessage() {
            return "imported";
        }
    }
}
