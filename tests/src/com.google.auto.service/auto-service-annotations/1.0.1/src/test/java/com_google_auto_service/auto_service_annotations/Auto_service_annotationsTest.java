/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_service.auto_service_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auto.service.AutoService;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;

public class Auto_service_annotationsTest {
    @Test
    void singleServiceAnnotationCanBeAppliedToAConcreteProvider() {
        GreetingService service = new GreetingProvider();

        assertThat(service.greet("AutoService")).isEqualTo("Hello, AutoService");
    }

    @Test
    void annotationCanDeclareMultipleServiceContractsForOneProvider() {
        MultiServiceProvider provider = new MultiServiceProvider();
        GreetingService greetingService = provider;
        Resettable resettable = provider;
        Runnable runnable = provider;

        assertThat(greetingService.greet("multiple services")).isEqualTo("Hello, multiple services");
        assertThat(resettable.reset()).isEqualTo("reset");

        runnable.run();
        assertThat(provider.hasRun()).isTrue();
    }

    @Test
    void annotationCanDeclareAnAbstractClassServiceContract() {
        MessageFormatter formatter = new BracketedMessageFormatter();

        assertThat(formatter.format("abstract service")).isEqualTo("[abstract service]");
        assertThat(formatter.description()).isEqualTo("bracketed formatter");
    }

    @Test
    void autoServiceValueExposesSingleServiceClass() {
        AutoService annotation = new AutoServiceLiteral(GreetingService.class);

        assertThat(annotation.annotationType()).isEqualTo(AutoService.class);
        assertThat(annotation.value()).containsExactly(GreetingService.class);
    }

    @Test
    void autoServiceValueExposesMultipleServiceClassesInDeclarationOrder() {
        AutoService annotation = new AutoServiceLiteral(GreetingService.class, Resettable.class, Runnable.class);

        assertThat(annotation.annotationType()).isEqualTo(AutoService.class);
        assertThat(annotation.value()).containsExactly(GreetingService.class, Resettable.class, Runnable.class);
    }

    interface GreetingService {
        String greet(String name);
    }

    interface Resettable {
        String reset();
    }

    abstract static class MessageFormatter {
        abstract String format(String message);

        String description() {
            return "generic formatter";
        }
    }

    @AutoService(GreetingService.class)
    static final class GreetingProvider implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    @AutoService({GreetingService.class, Resettable.class, Runnable.class})
    static final class MultiServiceProvider implements GreetingService, Resettable, Runnable {
        private boolean hasRun;

        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }

        @Override
        public String reset() {
            hasRun = false;
            return "reset";
        }

        @Override
        public void run() {
            hasRun = true;
        }

        boolean hasRun() {
            return hasRun;
        }
    }

    @AutoService(MessageFormatter.class)
    static final class BracketedMessageFormatter extends MessageFormatter {
        @Override
        String format(String message) {
            return "[" + message + "]";
        }

        @Override
        String description() {
            return "bracketed formatter";
        }
    }

    static final class AutoServiceLiteral implements AutoService {
        private final Class<?>[] services;

        AutoServiceLiteral(Class<?>... services) {
            this.services = services.clone();
        }

        @Override
        public Class<?>[] value() {
            return services.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return AutoService.class;
        }
    }
}
