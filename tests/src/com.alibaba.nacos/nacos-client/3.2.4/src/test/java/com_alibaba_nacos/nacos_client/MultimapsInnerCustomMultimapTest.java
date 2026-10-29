/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.Multimap;
import com.alibaba.nacos.shaded.com.google.common.collect.Multimaps;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void restoresCollectionFactoryForNewKeys() throws Exception {
        Multimap<String, String> map = Multimaps.newMultimap(new LinkedHashMap<>(), new MultimapFactories.Collections());
        map.put("group", "a");
        Multimap<String, String> restored = TestSupport.roundTrip(map);
        restored.put("new", "b");
        assertEquals(List.of("a"), restored.get("group"));
        assertEquals(List.of("b"), restored.get("new"));
    }
}
