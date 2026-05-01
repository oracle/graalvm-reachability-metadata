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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class DispatchingInvokerTest {
    @Test
    void dispatchesCallsAcrossMultipleDelegateInterfaces() {
        GreetingService proxy = Dispatching.proxy(GreetingService.class, CounterService.class)
                .with(new GreetingDelegate("Ada"), new CounterDelegate(10))
                .build();

        assertThat(proxy.greeting()).isEqualTo("Hello Ada");
        assertThat(((CounterService) proxy).next()).isEqualTo(11);
    }

    @Test
    void serializedDispatchingProxyRestoresMethodSetsForInvocation() throws Exception {
        GreetingService proxy = Dispatching.proxy(GreetingService.class, CounterService.class)
                .with(new GreetingDelegate("Grace"), new CounterDelegate(20))
                .build();

        byte[] serializedProxy = serialize(proxy);
        Object restoredProxy = deserialize(serializedProxy);

        assertThat(restoredProxy).isInstanceOf(GreetingService.class).isInstanceOf(CounterService.class);
        assertThat(((GreetingService) restoredProxy).greeting()).isEqualTo("Hello Grace");
        assertThat(((CounterService) restoredProxy).next()).isEqualTo(21);
    }

    private byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private Object deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    public interface GreetingService extends Serializable {
        String greeting();
    }

    public interface CounterService extends Serializable {
        int next();
    }

    public static final class GreetingDelegate implements GreetingService {
        private static final long serialVersionUID = 1L;

        private final String name;

        public GreetingDelegate(String name) {
            this.name = name;
        }

        @Override
        public String greeting() {
            return "Hello " + name;
        }
    }

    public static final class CounterDelegate implements CounterService {
        private static final long serialVersionUID = 1L;

        private final int value;

        public CounterDelegate(int value) {
            this.value = value;
        }

        @Override
        public int next() {
            return value + 1;
        }
    }
}
