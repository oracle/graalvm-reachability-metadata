/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.MapperFeature;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SetterlessPropertyTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(MapperFeature.INFER_PROPERTY_MUTATORS);
        Basket basket = mapper.readValue("""
                {"items":["apple","pear"]}
                """, Basket.class);
        assertEquals(List.of("apple", "pear"), basket.getItems());
    }

    public static class Basket {
        private final List<String> items = new ArrayList<>();

        public List<String> getItems() {
            return items;
        }
    }
}
