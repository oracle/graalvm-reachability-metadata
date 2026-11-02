/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.Pair;
import net.sf.geographiclib.PolygonArea;
import net.sf.geographiclib.PolygonResult;
import org.junit.jupiter.api.Test;

/** The geofence-editing subsystem previews boundary changes before publishing them. */
public class GeodesicFenceEditingTest {
    private static final double METER_TOLERANCE = 1.0e-6;

    @Test
    void previewsCandidateVerticesBeforePublishingGeofence() {
        PolygonArea boundary = new PolygonArea(Geodesic.WGS84, false);
        boundary.AddPoint(37.7749, -122.4194);
        boundary.AddPoint(34.0522, -118.2437);
        boundary.AddPoint(36.1699, -115.1398);
        Pair currentPoint = boundary.CurrentPoint();

        PolygonResult vertexPreview = boundary.TestPoint(33.4484, -112.0740, false, true);
        Pair pointAfterPreview = boundary.CurrentPoint();

        assertThat(vertexPreview.num).isEqualTo(4);
        assertThat(vertexPreview.perimeter).isPositive();
        assertThat(vertexPreview.area).isNotZero();
        assertThat(pointAfterPreview.first).isCloseTo(currentPoint.first, within(METER_TOLERANCE));
        assertThat(pointAfterPreview.second).isCloseTo(currentPoint.second, within(METER_TOLERANCE));

        boundary.AddPoint(33.4484, -112.0740);
        PolygonResult committedVertex = boundary.Compute(false, true);

        assertThat(committedVertex.num).isEqualTo(vertexPreview.num);
        assertThat(committedVertex.perimeter)
                .isCloseTo(vertexPreview.perimeter, within(METER_TOLERANCE));
        assertThat(committedVertex.area).isCloseTo(vertexPreview.area, within(METER_TOLERANCE));

        boundary.Clear();
        boundary.AddPoint(37.7749, -122.4194);
        PolygonResult edgePreview = boundary.TestEdge(90.0, 1_000.0, false, true);
        boundary.AddEdge(90.0, 1_000.0);
        PolygonResult committedEdge = boundary.Compute(false, true);

        assertThat(edgePreview.num).isEqualTo(committedEdge.num);
        assertThat(edgePreview.perimeter).isCloseTo(committedEdge.perimeter, within(METER_TOLERANCE));
        assertThat(edgePreview.area).isCloseTo(committedEdge.area, within(METER_TOLERANCE));
    }
}
