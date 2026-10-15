/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotatedMethodTest {
    @Test
    void mapsBeanFieldsMethodsAndRecordComponents() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InventoryItem item = new InventoryItem();
        item.setName("ledger");
        item.units = 4;

        String itemJson = mapper.writeValueAsString(item);
        InventoryItem decodedItem = mapper.readValue(itemJson, InventoryItem.class);
        Shipment decodedShipment = mapper.readValue(
                mapper.writeValueAsString(new Shipment("shipment-9", 3)), Shipment.class);

        assertThat(itemJson).contains("ledger", "units");
        assertThat(decodedItem.getName()).isEqualTo("ledger");
        assertThat(decodedItem.units).isEqualTo(4);
        assertThat(decodedShipment).isEqualTo(new Shipment("shipment-9", 3));
    }

    public static final class InventoryItem {
        public int units;
        private String name;

        public InventoryItem() { }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public record Shipment(String id, int units) { }
}
