/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class UnsafeAllocatorAnonymous1Test {
    public static class Configuration {
        private int replicas;
        private String name;

        public Configuration(int replicas, String name) {
            this.replicas = replicas;
            this.name = name;
        }
    }

    @Test
    void restoresFieldsOfObjectsWithOnlyParameterizedConstructors() {
        Gson gson = new Gson();
        Configuration value = new Configuration(3, "registry");
        Configuration restored = gson.fromJson(gson.toJson(value), Configuration.class);
        assertEquals(3, restored.replicas);
        assertEquals("registry", restored.name);
    }
}
