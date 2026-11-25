/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NoSuchMethodFailureAnalyzerTest {
    @Test
    void identifiesIncompatibleCallerAndCalleeInMissingMethodDiagnostics() {
        NoSuchMethodError failure = new NoSuchMethodError("'void " + Binder.class.getName() + ".configureEndpoint()'");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(SpringApplication.class.getName(), "run", "SpringApplication.java", 1)
        });
        SpringApplication application = new SpringApplication(NoSuchMethodFailureAnalyzerTest.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setApplicationContextFactory(ApplicationContextFactory.of(GenericApplicationContext::new));
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setListeners(List.of());
        application.setInitializers(List.of((AotApplicationContextInitializer<GenericApplicationContext>) context -> {
            throw failure;
        }));

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            System.setErr(captured);
            assertThatThrownBy(application::run).isInstanceOf(IllegalStateException.class).hasCause(failure);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("configureEndpoint()",
                SpringApplication.class.getName(), Binder.class.getName(),
                "available from the following locations:", "compatible versions");
    }
}
