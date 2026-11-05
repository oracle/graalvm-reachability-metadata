/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;
import java.util.Map;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' tuple subsystem to pair shipment identifiers with carrier labels.
public class ShipmentManifestPairTest {
    @Test
    @Timeout(60)
    public void pairsShipmentIdsWithCarriersForManifestProcessing() {
        ListIterable<String> shipmentIds = Lists.mutable.with("S-101", "S-102", "S-103");
        ListIterable<String> carrierLabels = Lists.mutable.with("northstar", "parcelpost", "northstar");

        ListIterable<Pair<String, String>> manifest = shipmentIds.zip(carrierLabels);
        Pair<String, String> firstShipment = manifest.getFirst();
        Pair<String, String> reversedFirstShipment = firstShipment.swap();
        Map.Entry<String, String> firstEntry = firstShipment.toEntry();

        assertEquals(3, manifest.size());
        assertEquals("S-101", firstShipment.getOne());
        assertEquals("northstar", firstShipment.getTwo());
        assertEquals("northstar", reversedFirstShipment.getOne());
        assertEquals("S-101", reversedFirstShipment.getTwo());
        assertEquals("S-101", firstEntry.getKey());
        assertEquals("northstar", firstEntry.getValue());
        assertIterableEquals(List.of("S-101", "S-102", "S-103"), manifest.collect(Pair::getOne));
        assertIterableEquals(List.of("northstar", "parcelpost", "northstar"), manifest.collect(Pair::getTwo));
    }
}
