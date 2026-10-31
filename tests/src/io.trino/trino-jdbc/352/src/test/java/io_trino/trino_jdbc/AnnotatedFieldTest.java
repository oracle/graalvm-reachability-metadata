/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JacksonInject;
import io.trino.jdbc.$internal.jackson.annotation.JsonMerge;
import io.trino.jdbc.$internal.jackson.databind.InjectableValues;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedFieldTest {
    @Test
    void mergesIntoExistingFieldContents() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Basket basket = new Basket();
        basket.items.add("first");
        mapper.readerForUpdating(basket).readValue("{\"items\":[\"second\"]}");
        assertEquals(List.of("first", "second"), basket.items);
    }

    @Test
    void injectsConfiguredValueIntoField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setInjectableValues(new InjectableValues.Std().addValue("warehouse", "north"));
        Shipment shipment = mapper.readValue("{\"item\":\"book\"}", Shipment.class);
        assertEquals("north", shipment.warehouse);
        assertEquals("book", shipment.item);
    }

    public static class Shipment {
        @JacksonInject("warehouse")
        public String warehouse;
        public String item;
    }

    public static class Basket {
        @JsonMerge
        public List<String> items = new ArrayList<>();
    }
}
