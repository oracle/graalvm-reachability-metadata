/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.aot.generate.FileSystemGeneratedFiles;
import org.springframework.aot.hint.ResourcePatternHint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestContextAotGenerator;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class MergedContextConfigurationRuntimeHintsTest {
    @Test
    void registersClasspathWebApplicationResourceBasePath(@TempDir Path generatedFilesPath) {
        TestContextAotGenerator generator = new TestContextAotGenerator(
                new FileSystemGeneratedFiles(generatedFilesPath));

        generator.processAheadOfTime(Stream.of(WebSpringTest.class));

        List<String> resourcePatterns = generator.getRuntimeHints().resources().resourcePatternHints()
                .flatMap(resourcePatternHints -> resourcePatternHints.getIncludes().stream())
                .map(ResourcePatternHint::getPattern)
                .toList();
        assertThat(resourcePatterns).contains("spring-test-webapp/*");
    }

    @ContextConfiguration(classes = WebApplicationConfiguration.class)
    @WebAppConfiguration("classpath:/spring-test-webapp")
    public static class WebSpringTest {
    }

    @Configuration(proxyBeanMethods = false)
    public static class WebApplicationConfiguration {
        @Bean
        public String webApplicationName() {
            return "spring-test";
        }
    }
}
