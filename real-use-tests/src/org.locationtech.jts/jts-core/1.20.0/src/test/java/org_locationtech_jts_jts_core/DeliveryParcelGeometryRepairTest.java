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
import org.locationtech.jts.geom.util.GeometryFixer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JTS's geometry repair subsystem to make an imported parcel boundary safe to publish.
public class DeliveryParcelGeometryRepairTest {
    @Test
    @Timeout(30)
    public void repairsSelfIntersectingParcelBoundary() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry importedParcel = geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(4, 4),
                new Coordinate(0, 4),
                new Coordinate(4, 0),
                new Coordinate(0, 0)
        });

        Geometry repairedParcel = GeometryFixer.fix(importedParcel);

        assertFalse(importedParcel.isValid());
        assertTrue(repairedParcel.isValid());
        assertEquals("MultiPolygon", repairedParcel.getGeometryType());
        assertEquals(2, repairedParcel.getNumGeometries());
        assertEquals(8.0, repairedParcel.getArea(), 0.0);
    }
}
