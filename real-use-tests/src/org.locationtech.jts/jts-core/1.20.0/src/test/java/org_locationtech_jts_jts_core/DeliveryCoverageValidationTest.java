/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.coverage.CoverageUnion;
import org.locationtech.jts.coverage.CoverageValidator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's coverage validation subsystem to verify adjacent delivery map tiles before publishing them.
public class DeliveryCoverageValidationTest {
    @Test
    @Timeout(30)
    public void validatesAdjacentTilesAndRejectsOverlappingTile() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry[] tiles = new Geometry[] {
                rectangle(geometryFactory, 0, 0, 10, 5),
                rectangle(geometryFactory, 0, 5, 10, 10)
        };

        assertTrue(CoverageValidator.isValid(tiles));

        Geometry mergedCoverage = CoverageUnion.union(tiles);
        assertEquals("Polygon", mergedCoverage.getGeometryType());
        assertEquals(100.0, mergedCoverage.getArea(), 0.0);
        assertEquals(7, mergedCoverage.getNumPoints());

        Geometry[] overlappingTiles = new Geometry[] {
                rectangle(geometryFactory, 0, 0, 10, 6),
                rectangle(geometryFactory, 0, 5, 10, 10)
        };
        assertFalse(CoverageValidator.isValid(overlappingTiles));
    }

    private static Polygon rectangle(
            GeometryFactory geometryFactory, double minX, double minY, double maxX, double maxY) {
        return geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        });
    }
}
