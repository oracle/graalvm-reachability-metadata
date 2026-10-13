/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.artoolkitplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.nearHull_2D;

import org.bytedeco.artoolkitplus.MarkerPoint;
import org.junit.jupiter.api.Test;

public class MarkerPointTest {
    @Test
    void computesConvexHullAroundMarkerPoints() {
        int[][] coordinates = {
            {5, 5}, {10, 10}, {0, 0}, {8, 3}, {0, 10}, {10, 0}, {2, 7}
        };

        try (MarkerPoint points = new MarkerPoint(coordinates.length);
                MarkerPoint hull = new MarkerPoint(coordinates.length + 1)) {
            for (int index = 0; index < coordinates.length; index++) {
                points.position(index).x(coordinates[index][0]).y(coordinates[index][1]);
            }

            int hullSize =
                    nearHull_2D(points.position(0), coordinates.length, 4, hull.position(0));
            int[] hullCoordinates = new int[hullSize * 2];
            for (int index = 0; index < hullSize; index++) {
                MarkerPoint point = hull.position(index);
                hullCoordinates[index * 2] = point.x();
                hullCoordinates[index * 2 + 1] = point.y();
            }

            assertThat(hullSize).isEqualTo(5);
            assertThat(hullCoordinates).containsExactly(0, 0, 10, 0, 10, 10, 0, 10, 0, 0);
        }
    }
}
