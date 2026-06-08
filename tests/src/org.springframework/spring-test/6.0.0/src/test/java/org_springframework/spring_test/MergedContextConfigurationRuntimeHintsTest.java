/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.util.Collection;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.ResourcePatternHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.aot.AotContextLoader;
import org.springframework.test.context.aot.TestContextAotGenerator;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class MergedContextConfigurationRuntimeHintsTest {
    private static final String WEB_RESOURCE_BASE_PATH =
            "classpath:/org_springframework/spring_test/webapp";

    private static final String WEB_RESOURCE_PATTERN =
            "org_springframework/spring_test/webapp/*";

    @Test
    void registersRuntimeHintsForWebMergedContextConfigurationResourceBasePath() {
        RuntimeHints runtimeHints = new RuntimeHints();
        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
        TestContextAotGenerator generator =
                new TestContextAotGenerator(generatedFiles, runtimeHints);

        try {
            generator.processAheadOfTime(Stream.of(WebApplicationTestCase.class));

            assertThat(runtimeHints.resources().resourcePatternHints()
                    .map(ResourcePatternHints::getIncludes)
                    .flatMap(Collection::stream)
                    .anyMatch(hint -> WEB_RESOURCE_PATTERN.equals(hint.getPattern())))
                    .isTrue();
        }
        catch (IllegalStateException ex) {
            assertThat(ex)
                    .hasMessage("Cannot perform AOT processing during AOT run-time execution");
        }
    }

    @WebAppConfiguration(WEB_RESOURCE_BASE_PATH)
    @ContextConfiguration(classes = TestConfiguration.class, loader = TestAotContextLoader.class)
    static class WebApplicationTestCase {
    }

    static class TestConfiguration {
    }

    public static class TestAotContextLoader implements AotContextLoader {
        @Override
        public void processContextConfiguration(ContextConfigurationAttributes configAttributes) {
        }

        @Override
        public ApplicationContext loadContext(MergedContextConfiguration mergedConfig) {
            GenericApplicationContext context = new GenericApplicationContext();
            context.refresh();
            return context;
        }

        @Override
        public ApplicationContext loadContextForAotProcessing(
                MergedContextConfiguration mergedConfig) {

            return new GenericApplicationContext();
        }

        @Override
        public ApplicationContext loadContextForAotRuntime(MergedContextConfiguration mergedConfig,
                ApplicationContextInitializer<ConfigurableApplicationContext> initializer) {

            GenericApplicationContext context = new GenericApplicationContext();
            initializer.initialize(context);
            context.refresh();
            return context;
        }
    }
}
