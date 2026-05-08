/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.MapMaker;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void serializesMapMakerInternalMapEntriesThroughProxy() throws Exception {
        ConcurrentMap<TimeUnit, TimeUnit> original = new MapMaker()
                .weakKeys()
                .weakValues()
                .makeMap();
        original.put(TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        original.put(TimeUnit.MINUTES, TimeUnit.MICROSECONDS);

        ConcurrentMap<TimeUnit, TimeUnit> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy)
                .containsEntry(TimeUnit.SECONDS, TimeUnit.MILLISECONDS)
                .containsEntry(TimeUnit.MINUTES, TimeUnit.MICROSECONDS)
                .hasSize(2);
    }

    private static ConcurrentMap<TimeUnit, TimeUnit> roundTrip(ConcurrentMap<TimeUnit, TimeUnit> original)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        Object copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            copy = input.readObject();
        }

        assertThat(copy).isInstanceOf(ConcurrentMap.class);
        @SuppressWarnings("unchecked")
        ConcurrentMap<TimeUnit, TimeUnit> typedCopy = (ConcurrentMap<TimeUnit, TimeUnit>) copy;
        return typedCopy;
    }
}
