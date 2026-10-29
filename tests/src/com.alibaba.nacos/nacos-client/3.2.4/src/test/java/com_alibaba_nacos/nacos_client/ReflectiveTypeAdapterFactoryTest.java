/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryTest {
    public static class Named {
        public String name;
    }

    public static class Configuration extends Named {
        private int replicas;
    }

    @Test
    void mapsInheritedAndPrivateFields() {
        Gson gson = new Gson();
        Configuration config = gson.fromJson("{\"name\":\"registry\",\"replicas\":3}", Configuration.class);
        assertEquals("registry", config.name);
        assertEquals(3, config.replicas);
        assertEquals(3, gson.fromJson(gson.toJson(config), Configuration.class).replicas);
    }
}
