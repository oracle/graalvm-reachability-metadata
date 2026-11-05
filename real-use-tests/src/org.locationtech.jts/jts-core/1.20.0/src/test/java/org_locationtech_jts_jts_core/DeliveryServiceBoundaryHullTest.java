/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's convex hull subsystem to derive a delivery service boundary from stop locations.
public class DeliveryServiceBoundaryHullTest {
    @Test
    @Timeout(30)
    public void derivesBoundaryAroundStopsAndExcludesAnUnservedLocation() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry deliveryStops = geometryFactory.createMultiPointFromCoords(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(10, 0),
                new Coordinate(10, 10),
                new Coordinate(0, 10),
                new Coordinate(4, 4),
                new Coordinate(6, 5)
        });

        Geometry serviceBoundary = new ConvexHull(deliveryStops).getConvexHull();
        Geometry servedStop = geometryFactory.createPoint(new Coordinate(5, 5));
        Geometry unservedLocation = geometryFactory.createPoint(new Coordinate(12, 5));

        assertEquals("Polygon", serviceBoundary.getGeometryType());
        assertEquals(100.0, serviceBoundary.getArea(), 0.0);
        assertEquals(5, serviceBoundary.getNumPoints());
        assertEquals(0.0, serviceBoundary.getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(10.0, serviceBoundary.getEnvelopeInternal().getMaxX(), 0.0);
        assertTrue(serviceBoundary.covers(servedStop));
        assertTrue(!serviceBoundary.covers(unservedLocation));
    }
}
