/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.MapMaker;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void restoresWeakKeyMapEntries() throws Exception {
        ConcurrentMap<Key, Integer> values = new MapMaker().weakKeys().makeMap();
        values.put(Key.ANSWER, 42);

        ConcurrentMap<Key, Integer> restored = SerializationSupport.roundTrip(values);

        assertThat(restored).containsEntry(Key.ANSWER, 42);
        assertThat(restored.putIfAbsent(Key.NEXT, 43)).isNull();
    }

    private enum Key {
        ANSWER,
        NEXT
    }
}
