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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's polygonization subsystem to assemble parcel boundaries into a delivery area.
public class DeliveryBoundaryPolygonizationTest {
    @Test
    @Timeout(30)
    public void assemblesParcelBoundaryLineworkIntoPolygon() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(List.of(
                line(geometryFactory, 0, 0, 10, 0),
                line(geometryFactory, 10, 0, 10, 10),
                line(geometryFactory, 10, 10, 0, 10),
                line(geometryFactory, 0, 10, 0, 0)));

        Collection<?> polygons = polygonizer.getPolygons();
        Polygon parcel = (Polygon) polygons.iterator().next();

        assertEquals(1, polygons.size());
        assertEquals(100.0, parcel.getArea(), 0.0);
        assertEquals(40.0, parcel.getLength(), 0.0);
        assertEquals(5.0, parcel.getCentroid().getX(), 0.0);
        assertEquals(5.0, parcel.getCentroid().getY(), 0.0);
        assertTrue(polygonizer.getDangles().isEmpty());
        assertTrue(polygonizer.getCutEdges().isEmpty());
        assertTrue(polygonizer.getInvalidRingLines().isEmpty());
    }

    private static LineString line(
            GeometryFactory geometryFactory, double x1, double y1, double x2, double y2) {
        return geometryFactory.createLineString(new Coordinate[] {
                new Coordinate(x1, y1),
                new Coordinate(x2, y2)
        });
    }
}
