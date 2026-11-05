/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises JTS's Voronoi tessellation subsystem to assign deliveries to the nearest hub.
public class DeliveryVoronoiZoneTest {
    @Test
    @Timeout(30)
    public void assignsDeliveryToTheHubOwningItsVoronoiZone() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry hubs = geometryFactory.createMultiPointFromCoords(new Coordinate[] {
                new Coordinate(2.5, 2.5),
                new Coordinate(7.5, 2.5),
                new Coordinate(2.5, 7.5),
                new Coordinate(7.5, 7.5)
        });
        Envelope serviceArea = new Envelope(0, 10, 0, 10);

        VoronoiDiagramBuilder diagramBuilder = new VoronoiDiagramBuilder();
        diagramBuilder.setSites(hubs);
        diagramBuilder.setClipEnvelope(serviceArea);
        Geometry deliveryZones = diagramBuilder.getDiagram(geometryFactory);

        assertEquals(4, deliveryZones.getNumGeometries());
        assertEquals(100.0, deliveryZones.getArea(), 0.0);
        for (int i = 0; i < deliveryZones.getNumGeometries(); i++) {
            assertEquals(25.0, deliveryZones.getGeometryN(i).getArea(), 0.0);
        }

        Point deliveryLocation = geometryFactory.createPoint(new Coordinate(2, 2));
        Geometry assignedZone = findZone(deliveryZones, deliveryLocation);

        assertNotNull(assignedZone);
        assertEquals(0.0, assignedZone.getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(5.0, assignedZone.getEnvelopeInternal().getMaxX(), 0.0);
        assertEquals(0.0, assignedZone.getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(5.0, assignedZone.getEnvelopeInternal().getMaxY(), 0.0);
    }

    private static Geometry findZone(Geometry deliveryZones, Point deliveryLocation) {
        for (int i = 0; i < deliveryZones.getNumGeometries(); i++) {
            Geometry zone = deliveryZones.getGeometryN(i);
            if (zone.covers(deliveryLocation)) {
                return zone;
            }
        }
        return null;
    }
}
