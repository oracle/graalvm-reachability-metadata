/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' object multimap subsystem to index shipments by delivery zone.
public class ShipmentZoneIndexTest {
    @Test
    @Timeout(60)
    public void buildsReadyShipmentQueuesForEveryZone() {
        MutableList<Shipment> shipments = Lists.mutable.with(
                new Shipment("S-101", 2, true, Lists.mutable.with("west")),
                new Shipment("S-102", 1, false, Lists.mutable.with("west")),
                new Shipment("S-103", 3, true, Lists.mutable.with("east")),
                new Shipment("S-104", 1, true, Lists.mutable.with("west", "east")));

        MutableListMultimap<String, Shipment> shipmentsByZone = shipments.groupByEach(Shipment::zones);
        MutableListMultimap<String, Shipment> readyByZone = shipmentsByZone.selectKeysValues(
                (zone, shipment) -> shipment.ready());

        MutableList<Shipment> westQueue = Lists.mutable.withAll(readyByZone.get("west"));
        westQueue.sortThisByInt(Shipment::priority);
        MutableList<String> westShipmentIds = westQueue.collect(Shipment::id);
        westShipmentIds.sortThis();

        assertEquals(5, shipmentsByZone.size());
        assertEquals(2, shipmentsByZone.keySet().size());
        assertIterableEquals(List.of("S-101", "S-104"), westShipmentIds);
        assertIterableEquals(List.of("S-103", "S-104"), readyByZone.get("east").collect(Shipment::id));
        assertEquals(1, westQueue.getFirst().priority());
    }

    private record Shipment(String id, int priority, boolean ready, MutableList<String> zones) {
    }
}
