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
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's binary geometry serialization subsystem to persist and restore a delivery boundary.
public class DeliveryBoundaryBinaryStorageTest {
    @Test
    @Timeout(30)
    public void roundTripsDeliveryBoundaryAsBinary() throws ParseException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry deliveryBoundary = geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(12, 0),
                new Coordinate(12, 10),
                new Coordinate(0, 10),
                new Coordinate(0, 0)
        });

        WKBWriter writer = new WKBWriter();
        byte[] storedBoundary = writer.write(deliveryBoundary);
        Geometry restoredBoundary = new WKBReader(geometryFactory).read(storedBoundary);

        assertTrue(storedBoundary.length > 0);
        assertEquals(deliveryBoundary.getGeometryType(), restoredBoundary.getGeometryType());
        assertEquals(120.0, restoredBoundary.getArea(), 0.0);
        assertTrue(deliveryBoundary.equalsExact(restoredBoundary));
        assertEquals(WKBWriter.toHex(storedBoundary), WKBWriter.toHex(writer.write(restoredBoundary)));
    }
}
