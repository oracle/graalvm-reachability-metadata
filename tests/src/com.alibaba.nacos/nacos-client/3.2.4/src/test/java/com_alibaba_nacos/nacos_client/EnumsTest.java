/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.base.Enums;
import com.alibaba.nacos.shaded.com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    public enum State {
        @SerializedName("ready")
        READY
    }

    @Test
    void readsAnnotationsFromEnumConstantsUsingTheEnumUtility() {
        assertEquals("ready", Enums.getField(State.READY).getAnnotation(SerializedName.class).value());
    }
}
