/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.toys.dispatch.Dispatching;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class DispatchingInvokerTest {
    @Test
    public void dispatchingProxyKeepsMethodDispatchAfterSerialization() throws Exception {
        Greeting proxy = Dispatching.proxy(Greeting.class, Counter.class)
                .with(new GreetingService(), new CounterService())
                .build();

        assertThat(proxy.greet("Ada")).isEqualTo("Hello Ada");
        assertThat(((Counter) proxy).increment(2)).isEqualTo(3);

        Greeting restoredProxy = roundTrip(proxy);

        assertThat(restoredProxy.greet("Grace")).isEqualTo("Hello Grace");
        assertThat(((Counter) restoredProxy).increment(41)).isEqualTo(42);
    }

    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            T restored = (T) input.readObject();
            return restored;
        }
    }

    public interface Greeting extends Serializable {
        String greet(String name);
    }

    public interface Counter extends Serializable {
        int increment(int value);
    }

    private static final class GreetingService implements Greeting {
        private static final long serialVersionUID = 1L;

        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    private static final class CounterService implements Counter {
        private static final long serialVersionUID = 1L;

        @Override
        public int increment(int value) {
            return value + 1;
        }
    }
}
