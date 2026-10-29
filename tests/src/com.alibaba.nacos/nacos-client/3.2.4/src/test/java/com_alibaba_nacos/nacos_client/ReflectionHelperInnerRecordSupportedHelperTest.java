/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ReflectionHelperInnerRecordSupportedHelperTest {
    public record Configuration(String name, int replicas) {
    }

    @Test
    void roundTripsRecordComponentsThroughGson() {
        Gson gson = new Gson();
        Configuration original = new Configuration("registry", 3);
        String json = gson.toJson(original);
        assertEquals(original, gson.fromJson(json, Configuration.class));
        assertEquals("registry", gson.fromJson(json, Configuration.class).name());
    }
}
