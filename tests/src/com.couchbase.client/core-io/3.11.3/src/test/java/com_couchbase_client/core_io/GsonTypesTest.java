/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import com.couchbase.client.core.deps.com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GsonTypesTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        TypeToken<List<String>[]> type = new TypeToken<List<String>[]>() { };
        List<String>[] values = gson.fromJson("""
                [["first","second"],["third"]]
                """, type);
        assertEquals(2, values.length);
        assertEquals(List.of("first", "second"), values[0]);
        assertEquals(List.of("third"), values[1]);
    }
}
