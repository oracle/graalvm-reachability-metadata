/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.util.List;

import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223InvocableScriptEngine;
import kotlin.sequences.Sequence;
import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class KotlinJsr223InvocableScriptEngineTest {
    @Test
    void adaptsAnExistingReceiverToAnInterface() {
        GreetingReceiver receiver = new GreetingReceiver("Hello");
        KotlinJsr223InvocableScriptEngine engine = new ReceiverEngine(receiver);

        Greeting greeting = engine.getInterface(receiver, Greeting.class);

        assertThat(greeting.greet("Ada")).isEqualTo("Hello, Ada");
        assertThat(greeting.greet("Lin")).isEqualTo("Hello, Lin");
    }

    @Test
    void adaptsTheReceiverHistoryToAnInterface() {
        KotlinJsr223InvocableScriptEngine engine = new ReceiverEngine(new GreetingReceiver("Welcome"));

        Greeting greeting = engine.getInterface(Greeting.class);

        assertThat(greeting.greet("Sam")).isEqualTo("Welcome, Sam");
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class GreetingReceiver {
        private final String salutation;

        public GreetingReceiver(String salutation) {
            this.salutation = salutation;
        }

        public String greet(String name) {
            return salutation + ", " + name;
        }
    }

    private static final class ReceiverEngine implements KotlinJsr223InvocableScriptEngine {
        private final Object receiver;

        private ReceiverEngine(Object receiver) {
            this.receiver = receiver;
        }

        @Override
        public InvokeWrapper getInvokeWrapper() {
            return null;
        }

        @Override
        public Sequence<Object> getBackwardInstancesHistory() {
            return () -> List.of(receiver).iterator();
        }

        @Override
        public ClassLoader getBaseClassLoader() {
            return Greeting.class.getClassLoader();
        }
    }
}
