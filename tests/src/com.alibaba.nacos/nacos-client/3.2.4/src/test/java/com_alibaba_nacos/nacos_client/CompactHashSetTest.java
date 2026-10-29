/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.alibaba.nacos.shaded.com.google.common.collect.SetMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.MultimapBuilder;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CompactHashSetTest {
    @Test
    void serializesHashSetValuesWithoutLosingUniqueness() throws Exception {
        SetMultimap<String, String> map = MultimapBuilder.hashKeys().hashSetValues().build();
        map.put("group", "a");
        map.put("group", "b");
        SetMultimap<String, String> restored = TestSupport.roundTrip(map);
        assertEquals(Set.of("a", "b"), restored.get("group"));
        assertFalse(restored.put("group", "a"));
    }
}
