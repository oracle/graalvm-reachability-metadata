/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import com.alibaba.nacos.shaded.com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

public class TypeAdaptersInnerEnumTypeAdapterTest {
    public enum Mode {
        @SerializedName(value = "active", alternate = {"enabled"})
        ACTIVE,
        PASSIVE
    }

    @Test
    void supportsAnnotatedEnumNamesAndAliases() {
        Gson gson = new Gson();
        assertEquals("\"active\"", gson.toJson(Mode.ACTIVE));
        assertEquals(Mode.ACTIVE, gson.fromJson("\"enabled\"", Mode.class));
        assertEquals(Mode.PASSIVE, gson.fromJson("\"PASSIVE\"", Mode.class));
    }
}
