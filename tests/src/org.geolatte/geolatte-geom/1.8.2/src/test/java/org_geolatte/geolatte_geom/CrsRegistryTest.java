/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_geolatte.geolatte_geom;

import static org.assertj.core.api.Assertions.assertThat;

import org.geolatte.geom.G2D;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.crs.CrsRegistry;
import org.geolatte.geom.crs.Geographic2DCoordinateReferenceSystem;
import org.geolatte.geom.crs.ProjectedCoordinateReferenceSystem;
import org.geolatte.geom.jts.JTS;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public class CrsRegistryTest {
    @Test
    void loadsGeographicAndProjectedEpsgDefinitions() {
        Geographic2DCoordinateReferenceSystem geographic =
                CrsRegistry.getGeographicCoordinateReferenceSystemForEPSG(4326);
        ProjectedCoordinateReferenceSystem projected =
                CrsRegistry.getProjectedCoordinateReferenceSystemForEPSG(3857);

        assertThat(geographic).isNotNull();
        assertThat(geographic.getName()).isEqualTo("WGS 84");
        assertThat(geographic.getCrsId().getCode()).isEqualTo(4326);
        assertThat(geographic.getCoordinateDimension()).isEqualTo(2);
        assertThat(projected).isNotNull();
        assertThat(projected.getCrsId().getCode()).isEqualTo(3857);
        assertThat(projected.getGeographicCoordinateSystem()).isEqualTo(geographic);
    }

    @Test
    void convertsJtsGeographyPointUsingItsRegisteredSrid() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Point jtsPoint = geometryFactory.createPoint(new Coordinate(4.35, 50.85));
        jtsPoint.setSRID(4326);

        Geometry<?> converted = JTS.from(jtsPoint);

        assertThat(converted.getSRID()).isEqualTo(4326);
        assertThat(converted.getCoordinateReferenceSystem())
                .isEqualTo(CrsRegistry.getGeographicCoordinateReferenceSystemForEPSG(4326));
        assertThat(converted.getPositions().getPositionN(0)).isEqualTo(new G2D(4.35, 50.85));
        assertThat(JTS.to(converted).equalsExact(jtsPoint)).isTrue();
        assertThat(JTS.to(converted).getSRID()).isEqualTo(4326);
    }
}
