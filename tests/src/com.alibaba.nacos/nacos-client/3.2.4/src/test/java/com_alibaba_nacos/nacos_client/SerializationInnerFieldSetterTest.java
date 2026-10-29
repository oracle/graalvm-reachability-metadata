/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.alibaba.nacos.shaded.com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.api.Test;

public class SerializationInnerFieldSetterTest {
    @Test
    void restoresImmutableMultimapStorageAndSize() throws Exception {
        ImmutableListMultimap<String, String> original = ImmutableListMultimap.of("a", "one", "a", "two");
        ImmutableListMultimap<String, String> restored = TestSupport.roundTrip(original);
        assertEquals(original, restored);
        assertEquals(2, restored.size());
        assertThrows(UnsupportedOperationException.class, () -> restored.put("a", "three"));
    }
}
