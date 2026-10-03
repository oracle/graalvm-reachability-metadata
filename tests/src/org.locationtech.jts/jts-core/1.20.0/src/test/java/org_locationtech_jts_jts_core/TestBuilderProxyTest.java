/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jtstest.function.FunctionsUtil;
import org.locationtech.jts.util.TestBuilderProxy;

public class TestBuilderProxyTest {
    @Test
    void delegatesIndicatorRequestsToAvailableTestBuilder() {
        Geometry geometry = new GeometryFactory().createPoint(new Coordinate(12, 34));

        assertThat(FunctionsUtil.getEnvelopeOrDefault(geometry))
                .isEqualTo(geometry.getEnvelopeInternal());
        assertThat(TestBuilderProxy.isActive()).isTrue();

        TestBuilderProxy.showIndicator(geometry);
        TestBuilderProxy.showIndicator(geometry, Color.BLUE);

        assertThat(geometry.getCoordinate()).isEqualTo(new Coordinate(12, 34));
    }
}
