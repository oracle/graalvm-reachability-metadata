/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_geolatte.geolatte_geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.geolatte.geom.builder.DSL.c;
import static org.geolatte.geom.builder.DSL.g;
import static org.geolatte.geom.builder.DSL.linestring;
import static org.geolatte.geom.builder.DSL.point;
import static org.geolatte.geom.crs.CoordinateReferenceSystems.PROJECTED_2D_METER;
import static org.geolatte.geom.crs.CoordinateReferenceSystems.WGS84;

import org.geolatte.geom.C2D;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.LineString;
import org.geolatte.geom.Point;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;

public class WktTest {
    @Test
    void roundTripsGeographicPointWithSrid() {
        Point<G2D> expected = point(WGS84, g(4.35, 50.85));
        String ewkt = "SRID=4326;POINT(4.35 50.85)";

        assertThat(Wkt.fromWkt(ewkt)).isEqualTo(expected);
        assertThat(Wkt.toWkt(expected)).isEqualTo(ewkt);
    }

    @Test
    void decodesAndEncodesLineStringsInEveryDialect() {
        LineString<C2D> expected = linestring(PROJECTED_2D_METER, c(1, 2), c(3, 4));
        for (Wkt.Dialect dialect : Wkt.Dialect.values()) {
            Geometry<C2D> decoded = Wkt.fromWkt("LINESTRING(1 2,3 4)", PROJECTED_2D_METER, dialect);
            String encoded = Wkt.toWkt(decoded, dialect);

            assertThat(decoded).as("decoded %s", dialect).isEqualTo(expected);
            assertThat(encoded).contains("LINESTRING", "1 2", "3 4");
            assertThat(Wkt.fromWkt(encoded, PROJECTED_2D_METER, dialect))
                    .as("round trip %s", dialect).isEqualTo(expected);
        }
    }
}
