/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's array-property access subsystem for a warehouse dispatch board.
 *
 * A warehouse application renders a fixed dispatch plan and lets an operator update the quantity
 * assigned to a particular dispatch slot through an OGNL-backed form.
 */
public class WarehouseDispatchArrayAccessTest {
    @Test
    @Timeout(60)
    void readsAndUpdatesDispatchSlotThroughArrayProperty() throws OgnlException {
        DispatchBoard board = DispatchBoard.sample();
        Map context = Ognl.createDefaultContext(board);

        Object secondSlotSku = Ognl.getValue("slots[1].sku", context, board);
        Object firstSlotQuantity = Ognl.getValue("slots[0].quantity", context, board);
        Ognl.setValue("slots[1].quantity", context, board, 18);
        Object updatedSecondSlotQuantity = Ognl.getValue(
                "slots[1].quantity", context, board);

        assertThat(secondSlotSku).isEqualTo("SKU-2048");
        assertThat(firstSlotQuantity).isEqualTo(12);
        assertThat(updatedSecondSlotQuantity).isEqualTo(18);
        assertThat(board.getSlots()[1].getQuantity()).isEqualTo(18);
    }

    public static final class DispatchBoard {
        private final DispatchSlot[] slots;

        private DispatchBoard(DispatchSlot[] slots) {
            this.slots = slots;
        }

        static DispatchBoard sample() {
            return new DispatchBoard(new DispatchSlot[] {
                new DispatchSlot("SKU-1024", 12),
                new DispatchSlot("SKU-2048", 7)
            });
        }

        public DispatchSlot[] getSlots() {
            return slots;
        }
    }

    public static final class DispatchSlot {
        private final String sku;
        private int quantity;

        private DispatchSlot(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
