/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_inject.javax_inject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Javax_injectTest {
    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
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
