/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

public class ObjectArraysTest {
    @Test
    void concatenatesArraysUsingTheirRequestedComponentType() {
        String[] result = ObjectArrays.concat(new String[] {"config"}, new String[] {"naming"}, String.class);
        assertEquals(String[].class, result.getClass());
        assertArrayEquals(new String[] {"config", "naming"}, result);
    }
}
