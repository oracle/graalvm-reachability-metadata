/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.alibaba.nacos.shaded.com.google.common.collect.SetMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.Multimaps;
import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void restoresSetFactoryAndDuplicateSemantics() throws Exception {
        SetMultimap<String, String> map = Multimaps.newSetMultimap(new LinkedHashMap<>(), new MultimapFactories.Sets());
        map.put("group", "a");
        SetMultimap<String, String> restored = TestSupport.roundTrip(map);
        assertFalse(restored.put("group", "a"));
        restored.put("new", "b");
        assertEquals(Set.of("b"), restored.get("new"));
    }
}
