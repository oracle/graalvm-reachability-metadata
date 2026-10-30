/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonDeserialize;
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BuilderBasedDeserializerTest {
    @Test
    void buildsImmutableValue() throws Exception {
        Order order = new ObjectMapper().readValue("{\"item\":\"book\",\"quantity\":2}", Order.class);
        assertEquals("book", order.item);
        assertEquals(2, order.quantity);
    }

    @JsonDeserialize(builder = OrderBuilder.class)
    public static class Order {
        public final String item;
        public final int quantity;

        public Order(String item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class OrderBuilder {
        private String item;
        private int quantity;

        public OrderBuilder item(String item) {
            this.item = item;
            return this;
        }

        public OrderBuilder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Order build() {
            return new Order(item, quantity);
        }
    }
}
