/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ArrayListMultimap;
import org.apache.pulsar.shade.com.google.common.collect.HashBiMap;
import org.apache.pulsar.shade.com.google.common.collect.HashMultiset;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class GoogleCommonCollectSerializationTest {

    @Test
    void preservesMapMultimapAndMultisetEntries() {
        final HashBiMap<String, Integer> map = HashBiMap.create();
        map.put("one", 1);
        map.put("two", 2);
        final ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("topic", "first");
        multimap.put("topic", "second");
        final HashMultiset<String> multiset = HashMultiset.create();
        multiset.add("message", 3);

        final HashBiMap<String, Integer> restoredMap = SerializationUtils.clone(map);
        final ArrayListMultimap<String, String> restoredMultimap = SerializationUtils.clone(multimap);
        final HashMultiset<String> restoredMultiset = SerializationUtils.clone(multiset);

        assertThat(restoredMap).containsExactlyInAnyOrderEntriesOf(map);
        assertThat(restoredMultimap.get("topic")).containsExactly("first", "second");
        assertThat(restoredMultiset.count("message")).isEqualTo(3);
    }
}
