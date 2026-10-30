/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_retry.spring_retry;

import org.junit.jupiter.api.Test;
import org.springframework.classify.util.SimpleMethodInvoker;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleMethodInvokerTest {

    @Test
    void invokesNamedMethodWithArgumentsAndReturnsItsResult() {
        ReceiptFormatter target = new ReceiptFormatter();
        SimpleMethodInvoker invoker = new SimpleMethodInvoker(target, "format", String.class, int.class);

        assertThat(invoker.invokeMethod("order-42", 3)).isEqualTo("order-42:3");
        assertThat(target.lastOrder).isEqualTo("order-42");
        assertThat(target.lastQuantity).isEqualTo(3);
        assertThat(target.calls).isEqualTo(1);

        assertThat(invoker.invokeMethod("order-73", 5)).isEqualTo("order-73:5");
        assertThat(target.lastOrder).isEqualTo("order-73");
        assertThat(target.lastQuantity).isEqualTo(5);
        assertThat(target.calls).isEqualTo(2);
    }

    @Test
    void invokesNamedMethodWithoutArguments() {
        ReceiptFormatter target = new ReceiptFormatter();
        SimpleMethodInvoker invoker = new SimpleMethodInvoker(target, "nextReceipt");

        assertThat(invoker.invokeMethod()).isEqualTo("receipt-1");
        assertThat(invoker.invokeMethod()).isEqualTo("receipt-2");
        assertThat(target.calls).isEqualTo(2);
    }

    public static class ReceiptFormatter {
        private String lastOrder;
        private int lastQuantity;
        private int calls;

        public String format(String order, int quantity) {
            lastOrder = order;
            lastQuantity = quantity;
            calls++;
            return order + ":" + quantity;
        }

        public String nextReceipt() {
            calls++;
            return "receipt-" + calls;
        }
    }
}
