/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.EnumHashBiMap;
import com.couchbase.client.core.deps.com.google.common.collect.HashMultimap;
import com.couchbase.client.core.deps.com.google.common.collect.HashMultiset;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {
    @Test
    void preservesContents() throws Exception {
        HashMultimap<String, String> multimap = HashMultimap.create();
        multimap.put("group", "alpha");
        multimap.put("group", "beta");
        assertEquals(multimap, SerializationSupport.roundTrip(multimap));
        HashMultiset<String> multiset = HashMultiset.create();
        multiset.add("alpha", 3);
        assertEquals(multiset, SerializationSupport.roundTrip(multiset));
        EnumHashBiMap<DayOfWeek, String> map = EnumHashBiMap.create(DayOfWeek.class);
        map.put(DayOfWeek.FRIDAY, "finish");
        assertEquals(map, SerializationSupport.roundTrip(map));
    }
}
