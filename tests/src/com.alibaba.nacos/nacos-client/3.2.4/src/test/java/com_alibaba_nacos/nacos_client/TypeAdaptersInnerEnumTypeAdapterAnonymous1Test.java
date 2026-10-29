/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import com.alibaba.nacos.api.config.ConfigType;
import org.junit.jupiter.api.Test;

public class TypeAdaptersInnerEnumTypeAdapterAnonymous1Test {
    @Test
    void discoversLibraryEnumConstantsForJsonMapping() {
        Gson gson = new Gson();
        assertEquals(ConfigType.YAML, gson.fromJson(gson.toJson(ConfigType.YAML), ConfigType.class));
        assertEquals(ConfigType.PROPERTIES, gson.fromJson(gson.toJson(ConfigType.PROPERTIES), ConfigType.class));
    }
}
