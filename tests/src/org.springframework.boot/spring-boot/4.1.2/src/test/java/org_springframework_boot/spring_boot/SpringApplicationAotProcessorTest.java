/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationAotProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringApplicationAotProcessorTest {
    @TempDir
    Path directory;

    @Test
    void preparesApplicationByInvokingMainWithArguments() {
        PreparingProcessor processor = new PreparingProcessor(ArgumentsApplication.class, this.directory,
                "--consumer.mode=batch");
        try (GenericApplicationContext context = processor.prepare()) {
            assertThat(context.getEnvironment().getProperty("consumer.mode")).isEqualTo("batch");
            context.refresh();
            assertThat(context.getBean("consumerMarker")).isEqualTo("prepared");
        }
    }

    @Test
    void preparesApplicationWithNoArgumentMainMethod() {
        PreparingProcessor processor = new PreparingProcessor(NoArgumentsApplication.class, this.directory);
        try (GenericApplicationContext context = processor.prepare()) {
            context.refresh();
            assertThat(context.getBean("consumerMarker")).isEqualTo("prepared");
        }
    }

    public static class PreparingProcessor extends SpringApplicationAotProcessor {
        public PreparingProcessor(Class<?> application, Path directory, String... args) {
            super(application, Settings.builder()
                    .sourceOutput(directory.resolve("sources"))
                    .resourceOutput(directory.resolve("resources"))
                    .classOutput(directory.resolve("classes"))
                    .groupId("consumer").artifactId("application").build(), args);
        }

        public GenericApplicationContext prepare() {
            return super.prepareApplicationContext(getApplicationClass());
        }
    }

    public static class ArgumentsApplication {
        public static void main(String[] args) {
            start(args);
        }
    }

    public static class NoArgumentsApplication {
        public static void main() {
            start();
        }
    }

    private static void start(String... args) {
        SpringApplication application = new SpringApplication(PrimarySource.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setApplicationContextFactory(ApplicationContextFactory.of(GenericApplicationContext::new));
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setListeners(List.of());
        application.addInitializers((AotApplicationContextInitializer<GenericApplicationContext>) context ->
                context.registerBean("consumerMarker", String.class, () -> "prepared"));
        application.run(args);
    }

    public static class PrimarySource {
    }
}
