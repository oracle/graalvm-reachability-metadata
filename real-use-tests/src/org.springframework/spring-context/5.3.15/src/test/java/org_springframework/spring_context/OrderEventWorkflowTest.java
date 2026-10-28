/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring application events to reserve inventory before issuing an order receipt.
public class OrderEventWorkflowTest {

    @Test
    @Timeout(60)
    public void reservesStockBeforeConfirmingAnOrder() {
        InventoryReservation inventory = new InventoryReservation(10);
        ReceiptSender receipts = new ReceiptSender(inventory);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            // Registration order is deliberately different from business processing order.
            context.addApplicationListener(receipts);
            context.addApplicationListener(inventory);
            context.refresh();

            context.publishEvent(new OrderPlacedEvent(this, "order-42", 3));

            assertEquals(7, inventory.available);
            assertEquals(List.of("order-42: reserved 3, remaining 7"), receipts.sent);

            context.publishEvent(new OrderPlacedEvent(this, "order-43", 2));

            assertEquals(5, inventory.available);
            assertEquals(List.of(
                    "order-42: reserved 3, remaining 7",
                    "order-43: reserved 2, remaining 5"), receipts.sent);
        }
    }

    public static final class OrderPlacedEvent extends ApplicationEvent {
        private final String orderId;
        private final int quantity;

        public OrderPlacedEvent(Object source, String orderId, int quantity) {
            super(source);
            this.orderId = orderId;
            this.quantity = quantity;
        }
    }

    public static final class InventoryReservation implements ApplicationListener<OrderPlacedEvent>, Ordered {
        private int available;

        public InventoryReservation(int available) {
            this.available = available;
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public void onApplicationEvent(OrderPlacedEvent event) {
            if (event.quantity > available) {
                throw new IllegalStateException("Insufficient inventory for " + event.orderId);
            }
            available -= event.quantity;
        }
    }

    public static final class ReceiptSender implements ApplicationListener<OrderPlacedEvent>, Ordered {
        private final InventoryReservation inventory;
        private final List<String> sent = new ArrayList<>();

        public ReceiptSender(InventoryReservation inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getOrder() {
            return 10;
        }

        @Override
        public void onApplicationEvent(OrderPlacedEvent event) {
            sent.add(event.orderId + ": reserved " + event.quantity + ", remaining " + inventory.available);
        }
    }
}
