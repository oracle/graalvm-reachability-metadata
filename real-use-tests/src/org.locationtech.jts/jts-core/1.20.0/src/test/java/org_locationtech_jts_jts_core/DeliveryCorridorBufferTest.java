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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's buffer subsystem to create a safe corridor around a delivery route.
public class DeliveryCorridorBufferTest {
    @Test
    @Timeout(30)
    public void buildsSafeCorridorAroundRoute() {
        GeometryFactory geometryFactory = new GeometryFactory();
        LineString route = geometryFactory.createLineString(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(10, 0)
        });

        Geometry corridor = route.buffer(2.0, 8);
        Geometry servedLocation = geometryFactory.createPoint(new Coordinate(5, 1.5));
        Geometry unservedLocation = geometryFactory.createPoint(new Coordinate(5, 2.1));

        assertEquals(-2.0, corridor.getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(12.0, corridor.getEnvelopeInternal().getMaxX(), 0.0);
        assertEquals(-2.0, corridor.getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(2.0, corridor.getEnvelopeInternal().getMaxY(), 0.0);
        assertEquals(52.48578060903222, corridor.getArea(), 1.0e-12);
        assertTrue(corridor.covers(servedLocation));
        assertFalse(corridor.covers(unservedLocation));
    }
}
