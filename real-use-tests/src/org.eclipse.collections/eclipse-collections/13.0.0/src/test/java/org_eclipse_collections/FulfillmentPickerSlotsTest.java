/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.FixedSizeList;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' fixed-size list subsystem for fulfillment picker slots.
public class FulfillmentPickerSlotsTest {
    @Test
    @Timeout(60)
    public void updatesAssignedSlotsWithoutChangingThePublishedWave() {
        FixedSizeList<PickSlot> pickerSlots = Lists.fixedSize.with(
                new PickSlot("A-01", "unassigned"),
                new PickSlot("A-02", "unassigned"),
                new PickSlot("A-03", "unassigned"));
        pickerSlots.set(0, new PickSlot("A-01", "order-104"));
        pickerSlots.set(1, new PickSlot("A-02", "order-105"));

        ImmutableList<PickSlot> publishedWave = pickerSlots.toImmutable();
        pickerSlots.set(1, new PickSlot("A-02", "order-108"));
        pickerSlots.set(2, new PickSlot("A-03", "order-109"));

        assertEquals(3, pickerSlots.size());
        assertEquals("order-108", pickerSlots.get(1).orderId());
        assertIterableEquals(
                List.of("order-104", "order-105", "unassigned"),
                publishedWave.collect(PickSlot::orderId));
        assertIterableEquals(
                List.of("order-104", "order-108", "order-109"),
                pickerSlots.collect(PickSlot::orderId));
    }

    private record PickSlot(String slotId, String orderId) {
    }
}
