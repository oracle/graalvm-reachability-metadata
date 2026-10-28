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

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.C2D;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.LineString;
import org.geolatte.geom.Point;
import org.geolatte.geom.codec.Wkb;
import org.junit.jupiter.api.Test;

public class WkbTest {
    @Test
    void decodesAndEncodesPostgisGeographyPoint() {
        String ewkb = "0101000020E6100000000000000000F03F0000000000000040";
        Point<G2D> expected = point(WGS84, g(1, 2));

        Geometry<?> decoded = Wkb.fromWkb(ByteBuffer.from(ewkb));

        assertThat(decoded).isEqualTo(expected);
        assertThat(decoded.getSRID()).isEqualTo(4326);
        assertThat(Wkb.toWkb(expected).toString()).isEqualTo(ewkb);
    }

    @Test
    void roundTripsLineStringsInEveryDialectAndByteOrder() {
        LineString<C2D> expected = linestring(PROJECTED_2D_METER, c(1.25, -2.5), c(7.5, 9));
        for (Wkb.Dialect dialect : Wkb.Dialect.values()) {
            for (ByteOrder byteOrder : new ByteOrder[] {ByteOrder.NDR, ByteOrder.XDR}) {
                ByteBuffer encoded = Wkb.toWkb(expected, byteOrder, dialect);
                Geometry<C2D> decoded = Wkb.newDecoder(dialect).decode(encoded, PROJECTED_2D_METER);

                assertThat(decoded).as("%s in %s byte order", dialect, byteOrder).isEqualTo(expected);
                assertThat(decoded.getPositions().size()).isEqualTo(2);
            }
        }
    }
}
