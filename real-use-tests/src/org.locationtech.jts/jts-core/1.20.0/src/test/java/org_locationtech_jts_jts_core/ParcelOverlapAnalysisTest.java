/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's constructive geometry subsystem to calculate a parcel's service-area overlap.
public class ParcelOverlapAnalysisTest {
    @Test
    @Timeout(30)
    public void reportsParcelOverlapWithServiceArea() throws ParseException {
        GeometryFactory geometryFactory = new GeometryFactory();
        WKTReader reader = new WKTReader(geometryFactory);
        Geometry serviceArea = reader.read(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        Geometry parcel = reader.read(
                "POLYGON ((5 -2, 12 -2, 12 6, 5 6, 5 -2))");

        Geometry overlap = parcel.intersection(serviceArea);
        Geometry outsideServiceArea = parcel.difference(serviceArea);
        Geometry combinedArea = parcel.union(serviceArea);

        assertTrue(parcel.intersects(serviceArea));
        assertEquals(30.0, overlap.getArea(), 0.0);
        assertEquals(26.0, outsideServiceArea.getArea(), 0.0);
        assertEquals(126.0, combinedArea.getArea(), 0.0);
        assertEquals(7.5, overlap.getCentroid().getX(), 0.0);
        assertEquals(3.0, overlap.getCentroid().getY(), 0.0);
    }
}
