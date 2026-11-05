/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's topology-preserving simplification subsystem to prepare a delivery route for a mobile map.
public class DeliveryRouteSimplificationTest {
    @Test
    @Timeout(30)
    public void simplifiesNoisyRouteWithoutChangingItsTripEndpoints() {
        GeometryFactory geometryFactory = new GeometryFactory();
        LineString recordedRoute = geometryFactory.createLineString(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(2, 0.1),
                new Coordinate(4, 0),
                new Coordinate(6, 3),
                new Coordinate(8, 3.1),
                new Coordinate(10, 3)
        });

        LineString mobileMapRoute = (LineString) TopologyPreservingSimplifier.simplify(
                recordedRoute, 0.25);

        assertEquals(6, recordedRoute.getNumPoints());
        assertEquals(4, mobileMapRoute.getNumPoints());
        assertEquals(recordedRoute.getCoordinateN(0), mobileMapRoute.getCoordinateN(0));
        assertEquals(recordedRoute.getCoordinateN(recordedRoute.getNumPoints() - 1),
                mobileMapRoute.getCoordinateN(mobileMapRoute.getNumPoints() - 1));
        assertEquals(0.0, mobileMapRoute.getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(10.0, mobileMapRoute.getEnvelopeInternal().getMaxX(), 0.0);
        assertEquals(0.0, mobileMapRoute.getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(3.0, mobileMapRoute.getEnvelopeInternal().getMaxY(), 0.0);
        assertTrue(mobileMapRoute.isValid());
    }
}
