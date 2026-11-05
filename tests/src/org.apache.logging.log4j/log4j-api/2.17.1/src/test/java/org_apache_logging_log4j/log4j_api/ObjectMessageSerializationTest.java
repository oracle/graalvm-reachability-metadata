/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;

import org.apache.logging.log4j.message.ObjectMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectMessageSerializationTest {

    @Test
    void keepsSerializablePayloadAndReplacesOthersWithText() throws Exception {
        ObjectMessage orderCopy = roundTrip(new ObjectMessage(new Order("o-7", 3)));
        ObjectMessage cartCopy = roundTrip(new ObjectMessage(new Cart(4)));

        assertThat(orderCopy.getParameters()).containsExactly(new Order("o-7", 3));
        assertThat(orderCopy.getFormattedMessage()).isEqualTo("Order[o-7 x3]");
        assertThat(cartCopy.getParameters()).containsExactly("Cart with 4 items");
        assertThat(cartCopy.getFormattedMessage()).isEqualTo("Cart with 4 items");
    }

    private static ObjectMessage roundTrip(ObjectMessage message) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(message);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ObjectMessage) in.readObject();
        }
    }

    public static final class Cart {

        private final int items;

        public Cart(int items) {
            this.items = items;
        }

        @Override
        public String toString() {
            return "Cart with " + items + " items";
        }
    }

    public static final class Order implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String id;

        private final int quantity;

        public Order(String id, int quantity) {
            this.id = id;
            this.quantity = quantity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Order order && order.id.equals(id) && order.quantity == quantity;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, quantity);
        }

        @Override
        public String toString() {
            return "Order[" + id + " x" + quantity + "]";
        }
    }
}
