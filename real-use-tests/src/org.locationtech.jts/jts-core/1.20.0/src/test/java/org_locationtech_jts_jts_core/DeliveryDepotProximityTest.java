/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's indexed distance subsystem to choose the nearest delivery depot.
public class DeliveryDepotProximityTest {
    @Test
    @Timeout(30)
    public void assignsOrderToNearestDepotAndRecordsHandoffPoint() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Depot centralDepot = new Depot("Central", geometryFactory.createLineString(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(0, 10)
        }));
        Depot eastDepot = new Depot("East", geometryFactory.createLineString(new Coordinate[] {
                new Coordinate(10, 0),
                new Coordinate(10, 10)
        }));
        Geometry customerLocation = geometryFactory.createPoint(new Coordinate(3, 4));

        Depot assignedDepot = List.of(centralDepot, eastDepot).stream()
                .min((left, right) -> Double.compare(
                        left.distanceTo(customerLocation), right.distanceTo(customerLocation)))
                .orElseThrow();
        Coordinate[] handoff = assignedDepot.nearestPoints(customerLocation);

        assertEquals("Central", assignedDepot.name());
        assertEquals(3.0, assignedDepot.distanceTo(customerLocation), 0.0);
        assertTrue(assignedDepot.isWithinDistance(customerLocation, 5.0));
        assertEquals(0.0, handoff[0].getX(), 0.0);
        assertEquals(4.0, handoff[0].getY(), 0.0);
        assertEquals(3.0, handoff[0].distance(handoff[1]), 0.0);
    }

    private static final class Depot {
        private final String name;
        private final IndexedFacetDistance roadDistance;

        private Depot(String name, LineString accessRoad) {
            this.name = name;
            this.roadDistance = new IndexedFacetDistance(accessRoad);
        }

        private double distanceTo(Geometry location) {
            return roadDistance.distance(location);
        }

        private boolean isWithinDistance(Geometry location, double threshold) {
            return roadDistance.isWithinDistance(location, threshold);
        }

        private Coordinate[] nearestPoints(Geometry location) {
            return roadDistance.nearestPoints(location);
        }

        private String name() {
            return name;
        }
    }
}
