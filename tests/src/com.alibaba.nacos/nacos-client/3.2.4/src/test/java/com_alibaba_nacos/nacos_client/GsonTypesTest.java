/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import com.alibaba.nacos.shaded.com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GsonTypesTest {
    @Test
    void deserializesGenericArrayComponents() {
        TypeToken<List<String>[]> type = new TypeToken<List<String>[]>() { };
        List<String>[] values = new Gson().fromJson("[[\"config\"],[\"naming\"]]", type.getType());
        assertEquals(2, values.length);
        assertEquals(List.of("config"), values[0]);
        assertEquals(List.of("naming"), values[1]);
    }
}
