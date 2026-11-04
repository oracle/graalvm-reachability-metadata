/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.TDecorators;
import gnu.trove.list.array.TLongArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Trove's primitive-list decorator subsystem for a shipment queue.
public class PrimitiveListDecoratorTest {
    @Test
    @Timeout(60)
    public void adaptsShipmentIdsToJavaCollectionApi() {
        TLongArrayList primitiveShipmentIds = new TLongArrayList();
        primitiveShipmentIds.add(new long[]{4101L, 4102L, 4103L});

        List<Long> shipmentQueue = TDecorators.wrap(primitiveShipmentIds);
        shipmentQueue.remove(Long.valueOf(4102L));
        shipmentQueue.add(4104L);
        shipmentQueue.set(0, 4199L);

        assertEquals(List.of(4199L, 4103L, 4104L), shipmentQueue);
        assertArrayEquals(new long[]{4199L, 4103L, 4104L}, primitiveShipmentIds.toArray());
    }
}
