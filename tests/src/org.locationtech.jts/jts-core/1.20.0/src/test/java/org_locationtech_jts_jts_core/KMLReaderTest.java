/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.kml.KMLReader;

public class KMLReaderTest {
    @Test
    void readsHomogeneousMultiGeometryAsMultiPoint() throws Exception {
        Geometry geometry = new KMLReader().read(
                "<MultiGeometry>"
                        + "<Point><coordinates>1,2</coordinates></Point>"
                        + "<Point><coordinates>3,4</coordinates></Point>"
                        + "</MultiGeometry>");

        assertThat(geometry.getGeometryType()).isEqualTo("MultiPoint");
        assertThat(geometry.getNumGeometries()).isEqualTo(2);
        assertThat(geometry.getEnvelopeInternal()).isEqualTo(new Envelope(1, 3, 2, 4));
    }
}
