/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's geometry precision reduction subsystem to normalize a parcel before map storage.
public class DeliveryParcelPrecisionNormalizationTest {
    @Test
    @Timeout(30)
    public void normalizesImportedParcelCoordinatesToMapGrid() throws ParseException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry importedParcel = new WKTReader(geometryFactory).read(
                "POLYGON ((0.04 0.02, 10.06 0.02, 10.06 5.04, 0.04 5.04, 0.04 0.02))");
        PrecisionModel mapGrid = new PrecisionModel(10.0);

        Geometry normalizedParcel = GeometryPrecisionReducer.reduce(importedParcel, mapGrid);

        assertTrue(normalizedParcel.isValid());
        assertEquals(50.5, normalizedParcel.getArea(), 0.0);
        assertEquals(0.0, normalizedParcel.getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(10.1, normalizedParcel.getEnvelopeInternal().getMaxX(), 0.0);
        assertEquals(0.0, normalizedParcel.getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(5.0, normalizedParcel.getEnvelopeInternal().getMaxY(), 0.0);
        assertEquals("POLYGON ((0 5, 10.1 5, 10.1 0, 0 0, 0 5))",
                new WKTWriter().write(normalizedParcel));
    }
}
