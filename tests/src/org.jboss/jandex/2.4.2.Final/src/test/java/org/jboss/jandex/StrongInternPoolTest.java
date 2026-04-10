/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.jandex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongInternPoolTest {
    @Test
    void serializationRoundTripPreservesInternedEntries() throws Exception {
        StrongInternPool<Object> pool = new StrongInternPool<>();
        String firstAlpha = new String("alpha");
        byte[] firstBytes = new byte[] { 1, 2, 3 };

        Object internedAlpha = pool.intern(firstAlpha);
        Object duplicateAlpha = pool.intern(new String("alpha"));
        Object internedBytes = pool.intern(firstBytes);
        Object duplicateBytes = pool.intern(new byte[] { 1, 2, 3 });
        Object internedNull = pool.intern(null);
        Object duplicateNull = pool.intern(null);

        assertThat(duplicateAlpha).isSameAs(internedAlpha);
        assertThat(duplicateBytes).isSameAs(internedBytes);
        assertThat(duplicateNull).isSameAs(internedNull);
        assertThat(pool.size()).isEqualTo(3);

        StrongInternPool<Object> roundTrippedPool = roundTrip(pool);

        assertThat(roundTrippedPool).isNotSameAs(pool);
        assertThat(roundTrippedPool.size()).isEqualTo(3);
        assertThat(roundTrippedPool.contains("alpha")).isTrue();
        assertThat(roundTrippedPool.contains(new byte[] { 1, 2, 3 })).isTrue();
        assertThat(roundTrippedPool.contains(null)).isTrue();

        List<Object> entries = new ArrayList<>();
        Iterator<Object> iterator = roundTrippedPool.iterator();
        while (iterator.hasNext()) {
            entries.add(iterator.next());
        }
        assertThat(entries).contains("alpha");
        assertThat(entries.stream().anyMatch(Objects::isNull)).isTrue();
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry).isInstanceOf(byte[].class);
            assertThat((byte[]) entry).containsExactly(1, 2, 3);
        });

        String storedAlpha = entries.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElseThrow();

        int sizeBeforeIntern = roundTrippedPool.size();
        Object reinternedAlpha = roundTrippedPool.intern(new String("alpha"));
        Object reinternedBytes = roundTrippedPool.intern(new byte[] { 1, 2, 3 });
        Object reinternedNull = roundTrippedPool.intern(null);

        assertThat(reinternedAlpha).isSameAs(storedAlpha);
        assertThat(reinternedBytes).isInstanceOf(byte[].class);
        assertThat((byte[]) reinternedBytes).containsExactly(1, 2, 3);
        assertThat(reinternedNull).isNull();
        assertThat(roundTrippedPool.size()).isEqualTo(sizeBeforeIntern);
    }

    private static <T> StrongInternPool<T> roundTrip(StrongInternPool<T> pool) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(pool);
        }

        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            @SuppressWarnings("unchecked")
            StrongInternPool<T> roundTrippedPool = (StrongInternPool<T>) objectInputStream.readObject();
            return roundTrippedPool;
        }
    }
}
