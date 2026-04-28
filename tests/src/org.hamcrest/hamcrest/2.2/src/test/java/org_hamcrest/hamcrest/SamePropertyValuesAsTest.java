/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hamcrest.hamcrest;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class SamePropertyValuesAsTest {

    @Test
    void samePropertyValuesMatcherReadsExpectedAndActualProperties() {
        InventoryItem expected = new InventoryItem("widget", 3);
        InventoryItem actual = new InventoryItem("widget", 3);

        MatcherAssert.assertThat(actual, Matchers.samePropertyValuesAs(expected));
    }

    public static final class InventoryItem {
        private final String sku;
        private final int quantity;

        public InventoryItem(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
