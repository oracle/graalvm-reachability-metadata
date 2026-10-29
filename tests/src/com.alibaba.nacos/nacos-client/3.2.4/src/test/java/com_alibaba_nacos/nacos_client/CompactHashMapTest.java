/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.ListMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.MultimapBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CompactHashMapTest {
    @Test
    void serializesHashKeyBackedMultimaps() throws Exception {
        ListMultimap<String, String> map = MultimapBuilder.hashKeys().arrayListValues().build();
        map.put("group", "a");
        map.put("group", "b");
        ListMultimap<String, String> restored = TestSupport.roundTrip(map);
        assertEquals(List.of("a", "b"), restored.get("group"));
        restored.put("new", "c");
        assertEquals(2, restored.keySet().size());
    }
}
