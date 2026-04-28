/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.toys.pool.Pool;
import com.thoughtworks.proxy.toys.pool.SerializationMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class PoolTest {
    @Test
    public void standardModeRoundTripsAvailableAndBorrowedInstances() throws Exception {
        Pool<Service> pool = Pool.create(Service.class)
                .with(new SerializableService("available"), new SerializableService("borrowed"))
                .build();
        Service borrowed = pool.get();

        assertThat(borrowed.name()).isEqualTo("available");

        Pool<Service> restoredPool = roundTrip(pool);

        assertThat(restoredPool.size()).isEqualTo(2);
        assertThat(restoredPool.get().name()).isEqualTo("borrowed");
        assertThat(restoredPool.get().name()).isEqualTo("available");
        assertThat(restoredPool.get()).isNull();
    }

    @Test
    public void noneModeRoundTripsPoolWithoutInstances() throws Exception {
        Pool<Service> pool = Pool.create(Service.class)
                .with(new SerializableService("discarded"))
                .mode(SerializationMode.NONE)
                .build();

        Pool<Service> restoredPool = roundTrip(pool);

        assertThat(restoredPool.size()).isZero();
        assertThat(restoredPool.get()).isNull();
    }

    @Test
    public void forceModeRoundTripsPoolWithoutNonSerializableInstances() throws Exception {
        Pool<Service> pool = Pool.create(Service.class)
                .with(new NonSerializableService("discarded"))
                .mode(SerializationMode.FORCE)
                .build();

        Pool<Service> restoredPool = roundTrip(pool);

        assertThat(restoredPool.size()).isZero();
        assertThat(restoredPool.get()).isNull();
    }

    private static Pool<Service> roundTrip(Pool<Service> pool) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pool);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            Pool<Service> restored = (Pool<Service>) input.readObject();
            return restored;
        }
    }

    public interface Service {
        String name();
    }

    private static final class SerializableService implements Service, Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;

        private SerializableService(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static final class NonSerializableService implements Service {
        private final String name;

        private NonSerializableService(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
