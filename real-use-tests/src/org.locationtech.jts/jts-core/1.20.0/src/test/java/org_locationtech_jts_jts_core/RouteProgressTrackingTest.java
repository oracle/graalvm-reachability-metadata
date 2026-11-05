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
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JTS's linear referencing subsystem to track a delivery vehicle along a route.
public class RouteProgressTrackingTest {
    @Test
    @Timeout(30)
    public void projectsLocationAndExtractsRouteSegment() {
        GeometryFactory geometryFactory = new GeometryFactory();
        LineString route = geometryFactory.createLineString(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(3, 0),
                new Coordinate(3, 4),
                new Coordinate(8, 4)
        });
        LengthIndexedLine progress = new LengthIndexedLine(route);

        double currentProgress = progress.project(new Coordinate(3.2, 1.5));
        Coordinate vehicleLocation = progress.extractPoint(currentProgress);
        LineString remainingRoute = (LineString) progress.extractLine(2, 9);

        assertEquals(12.0, progress.getEndIndex(), 0.0);
        assertEquals(4.5, currentProgress, 0.0);
        assertEquals(3.0, vehicleLocation.getX(), 0.0);
        assertEquals(1.5, vehicleLocation.getY(), 0.0);
        assertEquals(7.0, remainingRoute.getLength(), 0.0);
        assertEquals(2.0, remainingRoute.getCoordinateN(0).getX(), 0.0);
        assertEquals(5.0, remainingRoute.getCoordinateN(remainingRoute.getNumPoints() - 1).getX(), 0.0);
        assertEquals(4.0, remainingRoute.getCoordinateN(remainingRoute.getNumPoints() - 1).getY(), 0.0);
    }
}
