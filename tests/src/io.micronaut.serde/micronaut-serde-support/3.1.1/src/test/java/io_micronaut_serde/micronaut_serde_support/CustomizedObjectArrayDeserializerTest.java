/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_serde.micronaut_serde_support;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.Test;

public class CustomizedObjectArrayDeserializerTest {

    @Test
    void deserializesTypedObjectArray() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            InventoryItem[] items = mapper.readValue(
                    """
                    [
                      {"sku": "NOTEBOOK", "quantity": 2},
                      {"sku": "PENCIL", "quantity": 5}
                    ]
                    """,
                    InventoryItem[].class);

            assertThat(items).hasSize(2);
            assertThat(items[0].getSku()).isEqualTo("NOTEBOOK");
            assertThat(items[0].getQuantity()).isEqualTo(2);
            assertThat(items[1].getSku()).isEqualTo("PENCIL");
            assertThat(items[1].getQuantity()).isEqualTo(5);
        }
    }

    @Serdeable
    public static final class InventoryItem {

        private String sku;
        private int quantity;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
