/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ConstructorConstructorAnonymous9Test {
    public static class Configuration {
        public String name;
        public int replicas;

        public Configuration() {
            replicas = 4;
        }
    }

    @Test
    void invokesTheConstructorBeforeAssigningJsonFields() {
        Configuration config = new Gson().fromJson("{\"name\":\"registry\"}", Configuration.class);
        assertEquals("registry", config.name);
        assertEquals(4, config.replicas);
    }
}
