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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's affine transformation subsystem to align a delivery area with a map coordinate system.
public class DeliveryMapAlignmentTest {
    @Test
    @Timeout(30)
    public void alignsLocalDeliveryAreaWithMapCoordinates() throws ParseException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry localDeliveryArea = new WKTReader(geometryFactory).read(
                "POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))");

        AffineTransformation mapAlignment = AffineTransformation.scaleInstance(2.0, 3.0);
        mapAlignment.translate(100.0, 200.0);
        Geometry mappedDeliveryArea = mapAlignment.transform(localDeliveryArea);
        Point localOrder = geometryFactory.createPoint(new Coordinate(1, 2));
        Point mappedOrder = (Point) mapAlignment.transform(localOrder);

        assertTrue(mappedDeliveryArea.isValid());
        assertEquals(100.0, mappedDeliveryArea.getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(108.0, mappedDeliveryArea.getEnvelopeInternal().getMaxX(), 0.0);
        assertEquals(200.0, mappedDeliveryArea.getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(209.0, mappedDeliveryArea.getEnvelopeInternal().getMaxY(), 0.0);
        assertEquals(72.0, mappedDeliveryArea.getArea(), 0.0);
        assertEquals(102.0, mappedOrder.getX(), 0.0);
        assertEquals(206.0, mappedOrder.getY(), 0.0);
        assertTrue(mappedDeliveryArea.covers(mappedOrder));
    }
}
