/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ArrayTypeAdapterTest {
    @Test
    void readsPrimitiveAndReferenceArrays() {
        Gson gson = new Gson();
        assertArrayEquals(new int[] {1, 3}, gson.fromJson("[1,3]", int[].class));
        assertArrayEquals(new String[] {"config", "naming"}, gson.fromJson("[\"config\",\"naming\"]", String[].class));
    }
}
