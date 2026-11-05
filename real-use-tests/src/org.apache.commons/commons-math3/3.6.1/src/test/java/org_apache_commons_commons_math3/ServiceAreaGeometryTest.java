/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import java.util.List;

import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Computational geometry defines a delivery service area from courier stops.
public class ServiceAreaGeometryTest {
    @Test
    public void classifiesDeliveriesAgainstTheComputedServiceArea() {
        List<Vector2D> courierStops = List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(10.0, 0.0),
                new Vector2D(10.0, 8.0),
                new Vector2D(0.0, 8.0),
                new Vector2D(4.0, 3.0),
                new Vector2D(6.0, 5.0),
                new Vector2D(2.0, 6.0));

        ConvexHull2D serviceAreaHull = new MonotoneChain().generate(courierStops);
        Region<Euclidean2D> serviceArea = serviceAreaHull.createRegion();

        assertEquals(4, serviceAreaHull.getVertices().length);
        assertEquals(80.0, serviceArea.getSize(), 1.0e-10);
        assertEquals(36.0, serviceArea.getBoundarySize(), 1.0e-10);
        assertEquals(Region.Location.INSIDE, serviceArea.checkPoint(new Vector2D(5.0, 4.0)));
        assertEquals(Region.Location.OUTSIDE, serviceArea.checkPoint(new Vector2D(12.0, 4.0)));
        assertEquals(Region.Location.BOUNDARY, serviceArea.checkPoint(new Vector2D(0.0, 4.0)));
    }
}
