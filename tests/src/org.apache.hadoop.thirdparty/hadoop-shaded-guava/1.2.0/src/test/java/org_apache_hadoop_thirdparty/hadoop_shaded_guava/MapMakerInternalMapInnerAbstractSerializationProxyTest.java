/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;

import org.apache.hadoop.thirdparty.com.google.common.collect.MapMaker;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void roundTripsWeakValueMapThroughMapMakerSerializationProxy() throws Exception {
        ConcurrentMap<String, Integer> original = new MapMaker()
                .initialCapacity(8)
                .concurrencyLevel(1)
                .weakValues()
                .makeMap();
        original.put("alpha", 1);
        original.put("beta", 2);

        @SuppressWarnings("unchecked")
        ConcurrentMap<String, Integer> restored =
                (ConcurrentMap<String, Integer>) roundTrip(original);

        assertThat(restored).hasSize(2);
        assertThat(restored).containsEntry("alpha", 1).containsEntry("beta", 2);
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            return inputStream.readObject();
        }
    }
}
