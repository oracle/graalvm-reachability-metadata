/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.ImmutableListMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.LinkedHashMultiset;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void preservesMultisetCountsAndMultimapContents() throws Exception {
        LinkedHashMultiset<String> multiset = LinkedHashMultiset.create();
        multiset.add("replica", 3);
        LinkedHashMultiset<String> restoredSet = TestSupport.roundTrip(multiset);
        assertEquals(3, restoredSet.count("replica"));
        ImmutableListMultimap<String, String> multimap = ImmutableListMultimap.of("group", "first", "group", "second");
        assertEquals(multimap, TestSupport.roundTrip(multimap));
    }
}
