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
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JTS's Delaunay triangulation subsystem to divide a delivery area into site regions.
public class DeliverySiteTriangulationTest {
    @Test
    @Timeout(30)
    public void buildsCoverageMeshFromDeliverySites() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry deliverySites = geometryFactory.createMultiPointFromCoords(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(10, 0),
                new Coordinate(10, 10),
                new Coordinate(0, 10)
        });

        DelaunayTriangulationBuilder triangulation = new DelaunayTriangulationBuilder();
        triangulation.setSites(deliverySites);
        Geometry coverageMesh = triangulation.getTriangles(geometryFactory);

        assertEquals(2, coverageMesh.getNumGeometries());
        assertEquals(100.0, coverageMesh.getArea(), 0.0);
        assertEquals(50.0, coverageMesh.getGeometryN(0).getArea(), 0.0);
        assertEquals(50.0, coverageMesh.getGeometryN(1).getArea(), 0.0);
    }
}
