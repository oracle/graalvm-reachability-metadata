/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.LazyForwardingEquality;

public class LazyForwardingEqualityTest {
    @Test
    void serializesAndDeserializesCalculatedState() throws Exception {
        CountingEquality.resetCalculations();
        final CountingEquality original = new CountingEquality("primary input");

        assertThat(CountingEquality.calculations()).isZero();

        final byte[] serialized = serialize(original);

        assertThat(CountingEquality.calculations()).isEqualTo(1);

        final CountingEquality restored = deserialize(serialized);

        assertThat(CountingEquality.calculations()).isEqualTo(1);
        assertThat(restored.toBytes()).isEqualTo(original.toBytes());
        assertThat(restored).isEqualTo(original);
        assertThat(restored.hashCode()).isEqualTo(original.hashCode());
        assertThat(CountingEquality.calculations()).isEqualTo(1);
    }

    private static byte[] serialize(CountingEquality equality) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(equality);
        }
        return bytes.toByteArray();
    }

    private static CountingEquality deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (CountingEquality) inputStream.readObject();
        }
    }

    private static final class CountingEquality extends LazyForwardingEquality<EqualityState> {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger CALCULATIONS = new AtomicInteger();

        private final String input;

        private CountingEquality(String input) {
            this.input = input;
        }

        private static void resetCalculations() {
            CALCULATIONS.set(0);
        }

        private static int calculations() {
            return CALCULATIONS.get();
        }

        @Override
        protected EqualityState calculateState() {
            CALCULATIONS.incrementAndGet();
            return new EqualityState(input.trim(), input.toUpperCase());
        }
    }

    private static final class EqualityState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String normalized;
        private final String display;

        private EqualityState(String normalized, String display) {
            this.normalized = normalized;
            this.display = display;
        }
    }
}
