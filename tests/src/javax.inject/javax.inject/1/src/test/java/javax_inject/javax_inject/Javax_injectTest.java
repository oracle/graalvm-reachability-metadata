/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_inject.javax_inject;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

class Javax_injectTest {
    @Test
    void injectAndNamedAnnotationsSupportCommonInjectionPointShapes() {
        NamedGreetingComponent component = new NamedGreetingComponent("Hello");

        component.punctuation = "!";
        component.setRecipient("world");

        assertThat(component.composeGreeting()).isEqualTo("Hello, world!");
    }

    @Test
    void customQualifierScopeAndSingletonAnnotationsCanBeAppliedToTypes() {
        RemoteGreetingFacade facade = new RemoteGreetingFacade(new GreetingTemplates(), new RemoteFormatter());

        assertThat(facade.greet("team")).isEqualTo("remote hello, team");
    }

    @Test
    void providerCanCreateFreshInstancesOnDemand() {
        Provider<MessageHolder> provider = new SequencedMessageProvider();

        MessageHolder first = provider.get();
        MessageHolder second = provider.get();

        assertThat(first).isNotSameAs(second);
        assertThat(first.message()).isEqualTo("message-1");
        assertThat(second.message()).isEqualTo("message-2");
    }

    @Test
    void providerReadsCurrentStateLazily() {
        AtomicReference<String> prefix = new AtomicReference<>("hello");
        Provider<String> provider = new GreetingProvider(prefix);

        String initialGreeting = provider.get();
        prefix.set("hi");
        String updatedGreeting = provider.get();

        assertThat(initialGreeting).isEqualTo("hello, world");
        assertThat(updatedGreeting).isEqualTo("hi, world");
    }

    private static final class NamedGreetingComponent {
        private final String greeting;

        @Inject
        @Named("punctuation")
        private String punctuation;

        private String recipient;

        @Inject
        private NamedGreetingComponent(@Named("greeting") String greeting) {
            this.greeting = greeting;
        }

        @Inject
        private void setRecipient(@Named("recipient") String recipient) {
            this.recipient = recipient;
        }

        private String composeGreeting() {
            return this.greeting + ", " + this.recipient + this.punctuation;
        }
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, FIELD, PARAMETER, METHOD})
    private @interface Remote {
    }

    @Scope
    @Retention(RUNTIME)
    @Target(TYPE)
    private @interface SessionScoped {
    }

    @Singleton
    private static final class GreetingTemplates {
        private String greetingFor(String name) {
            return "hello, " + name;
        }
    }

    private interface GreetingFormatter {
        String format(String message);
    }

    @Remote
    private static final class RemoteFormatter implements GreetingFormatter {
        @Override
        public String format(String message) {
            return "remote " + message;
        }
    }

    @SessionScoped
    private static final class RemoteGreetingFacade {
        private final GreetingTemplates templates;
        private final GreetingFormatter formatter;

        @Inject
        private RemoteGreetingFacade(GreetingTemplates templates, @Remote GreetingFormatter formatter) {
            this.templates = templates;
            this.formatter = formatter;
        }

        private String greet(String name) {
            return this.formatter.format(this.templates.greetingFor(name));
        }
    }

    private static final class SequencedMessageProvider implements Provider<MessageHolder> {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public MessageHolder get() {
            int nextValue = this.sequence.incrementAndGet();
            return new MessageHolder("message-" + nextValue);
        }
    }

    private static final class GreetingProvider implements Provider<String> {
        private final AtomicReference<String> prefix;

        private GreetingProvider(AtomicReference<String> prefix) {
            this.prefix = prefix;
        }

        @Override
        public String get() {
            return this.prefix.get() + ", world";
        }
    }

    private static final class MessageHolder {
        private final String message;

        private MessageHolder(String message) {
            this.message = message;
        }

        private String message() {
            return this.message;
        }
    }
}
