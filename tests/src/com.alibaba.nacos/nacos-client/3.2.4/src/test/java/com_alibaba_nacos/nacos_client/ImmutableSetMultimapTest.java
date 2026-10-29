/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.ImmutableSetMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ImmutableSetMultimapTest {
    @Test
    void retainsSortedValuesAndComparator() throws Exception {
        ImmutableSetMultimap<String, String> map = ImmutableSetMultimap.<String, String>builder()
                .orderValuesBy(Ordering.natural()).put("group", "b").put("group", "a").build();
        ImmutableSetMultimap<String, String> restored = TestSupport.roundTrip(map);
        assertEquals(List.of("a", "b"), new ArrayList<>(restored.get("group")));
        assertEquals(map, restored);
        assertEquals(2, restored.size());
    }
}
