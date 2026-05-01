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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class PoolTest {
    @Test
    void forceSerializationPreservesAvailableAndBorrowedInstances() throws Exception {
        Pool<Resource> pool = Pool.create(Resource.class)
                .with(new ResourceImpl("available"), new ResourceImpl("borrowed"))
                .mode(SerializationMode.FORCE)
                .build();

        Resource borrowedResource = pool.get();
        assertThat(borrowedResource.name()).isEqualTo("available");
        assertThat(pool.getAvailable()).isEqualTo(1);

        Pool<Resource> restoredPool = deserializePool(serialize(pool));

        assertThat(restoredPool.size()).isEqualTo(2);
        assertThat(restoredPool.getAvailable()).isEqualTo(2);
        assertThat(resourceNamesFrom(restoredPool)).containsExactlyInAnyOrder("available", "borrowed");
    }

    @Test
    void noneSerializationRestoresAnEmptyUsablePool() throws Exception {
        Pool<Resource> pool = Pool.create(Resource.class)
                .with(new ResourceImpl("discarded"))
                .mode(SerializationMode.NONE)
                .build();

        Pool<Resource> restoredPool = deserializePool(serialize(pool));

        assertThat(restoredPool.size()).isZero();
        assertThat(restoredPool.getAvailable()).isZero();

        restoredPool.add(new ResourceImpl("repopulated"));
        Resource resource = restoredPool.get();
        assertThat(resource.name()).isEqualTo("repopulated");
    }

    private byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private Pool<Resource> deserializePool(byte[] serializedPool) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedPool))) {
            @SuppressWarnings("unchecked")
            Pool<Resource> restoredPool = Pool.class.cast(input.readObject());
            return restoredPool;
        }
    }

    private Set<String> resourceNamesFrom(Pool<Resource> pool) {
        Set<String> names = new HashSet<>();
        Resource firstResource = pool.get();
        Resource secondResource = pool.get();
        names.add(firstResource.name());
        names.add(secondResource.name());
        return names;
    }

    public interface Resource extends Serializable {
        String name();
    }

    public static final class ResourceImpl implements Resource {
        private static final long serialVersionUID = 1L;

        private final String name;

        public ResourceImpl(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
