/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.ImmutableListMultimap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ImmutableListMultimapTest {
    @Test
    void retainsDuplicateValuesAndListOrder() throws Exception {
        ImmutableListMultimap<String, String> map = ImmutableListMultimap.of("group", "b", "group", "a", "group", "b");
        ImmutableListMultimap<String, String> restored = TestSupport.roundTrip(map);
        assertEquals(List.of("b", "a", "b"), restored.get("group"));
        assertEquals(3, restored.inverse().size());
    }
}
