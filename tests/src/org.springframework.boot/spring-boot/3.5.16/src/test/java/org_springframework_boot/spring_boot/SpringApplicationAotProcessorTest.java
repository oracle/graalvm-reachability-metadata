/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
    Path output;

    @Test
    void generatesInitializerFromApplicationMainWithArguments() throws Exception {
        ArgumentsApplication.receivedMode = null;

        SpringApplicationAotProcessor.main(new String[] {ArgumentsApplication.class.getName(),
                this.output.resolve("sources").toString(), this.output.resolve("resources").toString(),
                this.output.resolve("classes").toString(), "example", "mail", "--mail.mode=outbound"});

        assertThat(ArgumentsApplication.receivedMode).isEqualTo("--mail.mode=outbound");
        assertInitializerGenerated();
    }

    @Test
    void generatesInitializerFromApplicationMainWithoutArguments() throws Exception {
        NoArgumentsApplication.started = false;

        SpringApplicationAotProcessor.main(new String[] {NoArgumentsApplication.class.getName(),
                this.output.resolve("sources").toString(), this.output.resolve("resources").toString(),
                this.output.resolve("classes").toString(), "example", "mail"});

        assertThat(NoArgumentsApplication.started).isTrue();
        assertInitializerGenerated();
    }

    private void assertInitializerGenerated() throws IOException {
        try (Stream<Path> sources = Files.walk(this.output.resolve("sources"))) {
            List<Path> initializers = sources.filter(path -> path.getFileName().toString()
                    .endsWith("__ApplicationContextInitializer.java")).toList();
            assertThat(initializers).singleElement().satisfies(path ->
                    assertThat(path).content().contains("ApplicationContextInitializer", "initialize"));
        }
    }

    private static void startApplication(String... args) {
        SpringApplication application = new SpringApplication(MailService.class);
        application.setMainApplicationClass(MailService.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setApplicationContextFactory(ApplicationContextFactory.of(GenericApplicationContext::new));
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setListeners(List.of());
        application.addInitializers((AotApplicationContextInitializer<GenericApplicationContext>) context ->
                context.registerBean("mailService", MailService.class));
        application.run(args);
    }

    public static class ArgumentsApplication {

        static String receivedMode;

        public static void main(String[] args) {
            receivedMode = args[0];
            startApplication(args);
        }
    }

    public static class NoArgumentsApplication {

        static boolean started;

        public static void main() {
            started = true;
            startApplication();
        }
    }

    public static class MailService {

        public String status() {
            return "ready";
        }
    }
}
