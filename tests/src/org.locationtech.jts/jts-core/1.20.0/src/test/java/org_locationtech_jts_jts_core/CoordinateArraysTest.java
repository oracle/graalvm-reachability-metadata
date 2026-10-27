/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.CoordinateXY;

public class CoordinateArraysTest {
    @Test
    void convertsMixedCoordinateTypesToAConsistentArray() {
        Coordinate[] mixed = {
            new CoordinateXY(1, 2),
            new Coordinate(3, 4, 5)
        };

        Coordinate[] consistent = CoordinateArrays.enforceConsistency(mixed, 3, 0);

        assertThat(consistent).hasSize(2);
        assertThat(consistent.getClass().getComponentType()).isEqualTo(Coordinate.class);
        assertThat(consistent[0].getX()).isEqualTo(1);
        assertThat(consistent[0].getY()).isEqualTo(2);
        assertThat(consistent[1]).isEqualTo(new Coordinate(3, 4, 5));
    }
}
