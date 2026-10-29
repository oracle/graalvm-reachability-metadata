/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import com.alibaba.nacos.shaded.com.google.gson.GsonBuilder;
import com.alibaba.nacos.shaded.com.google.gson.ReflectionAccessFilter;
import org.junit.jupiter.api.Test;

public class ReflectionAccessFilterHelperInnerAccessCheckerAnonymous1Test {
    public static class Configuration {
        public String name;

        public Configuration() {
        }
    }

    @Test
    void honorsAccessiblePublicMembersWithAnAccessFilter() {
        Gson gson = new GsonBuilder().addReflectionAccessFilter(type ->
                ReflectionAccessFilter.FilterResult.BLOCK_INACCESSIBLE).create();
        Configuration config = gson.fromJson("{\"name\":\"registry\"}", Configuration.class);
        assertEquals("registry", config.name);
        assertEquals("registry", gson.fromJson(gson.toJson(config), Configuration.class).name);
    }
}
