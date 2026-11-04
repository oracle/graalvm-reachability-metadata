/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.list.TLinkableAdapter;
import gnu.trove.list.linked.TLinkedList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises Trove's linked-list subsystem to maintain a mutable shipment route.
public class ShipmentRouteLinkedListTest {
    @Test
    @Timeout(60)
    public void insertsAndRemovesStopsWhileKeepingRouteLinksConsistent() {
        TLinkedList<RouteStop> route = new TLinkedList<>();
        RouteStop depot = new RouteStop("DEPOT", "dispatch");
        RouteStop customer = new RouteStop("CUSTOMER-7", "delivery");
        RouteStop pickup = new RouteStop("PICKUP-3", "pickup");
        RouteStop inspection = new RouteStop("INSPECTION", "quality-check");

        route.addLast(depot);
        route.addLast(customer);
        route.addAfter(depot, pickup);
        route.addBefore(customer, inspection);

        assertEquals(List.of("DEPOT", "PICKUP-3", "INSPECTION", "CUSTOMER-7"), routeIds(route));
        assertSame(pickup, route.getNext(depot));
        assertSame(inspection, route.getPrevious(customer));
        assertEquals("quality-check", inspection.stopType());

        route.remove(pickup);
        assertEquals(List.of("DEPOT", "INSPECTION", "CUSTOMER-7"), routeIds(route));
        assertSame(inspection, route.getNext(depot));
        assertSame(customer, route.getLast());
    }

    private static List<String> routeIds(TLinkedList<RouteStop> route) {
        List<String> ids = new ArrayList<>();
        for (RouteStop stop = route.getFirst(); stop != null; stop = route.getNext(stop)) {
            ids.add(stop.stopId());
        }
        return ids;
    }

    private static final class RouteStop extends TLinkableAdapter<RouteStop> {
        private final String stopId;
        private final String stopType;

        private RouteStop(String stopId, String stopType) {
            this.stopId = stopId;
            this.stopType = stopType;
        }

        private String stopId() {
            return stopId;
        }

        private String stopType() {
            return stopType;
        }
    }
}
