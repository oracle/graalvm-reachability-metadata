/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JTS's spatial indexing subsystem to locate customers in delivery zones.
public class DeliveryZoneLookupTest {
    @Test
    @Timeout(30)
    public void findsDeliveryZoneFromIndexedBoundaries() throws ParseException {
        GeometryFactory geometryFactory = new GeometryFactory();
        WKTReader reader = new WKTReader(geometryFactory);
        DeliveryZone central = new DeliveryZone(
                "Central",
                reader.read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"));
        DeliveryZone west = new DeliveryZone(
                "West",
                reader.read("POLYGON ((-10 0, 0 0, 0 10, -10 10, -10 0))"));
        DeliveryZone south = new DeliveryZone(
                "South",
                reader.read("POLYGON ((0 -10, 10 -10, 10 0, 0 0, 0 -10))"));

        STRtree zones = new STRtree();
        for (DeliveryZone zone : List.of(central, west, south)) {
            zones.insert(zone.boundary().getEnvelopeInternal(), zone);
        }

        assertEquals(3, zones.size());
        assertEquals("Central", findZone(zones, reader.read("POINT (5 5)")));
        assertEquals("West", findZone(zones, reader.read("POINT (-5 5)")));
        assertEquals("South", findZone(zones, reader.read("POINT (5 -5)")));
        assertEquals("Unserved", findZone(zones, reader.read("POINT (20 20)")));
    }

    private static String findZone(STRtree zones, Geometry location) {
        for (Object candidate : zones.query(location.getEnvelopeInternal())) {
            DeliveryZone zone = (DeliveryZone) candidate;
            if (zone.contains(location)) {
                return zone.name();
            }
        }
        return "Unserved";
    }

    private static final class DeliveryZone {
        private final String name;
        private final Geometry boundary;
        private final PreparedGeometry preparedBoundary;

        private DeliveryZone(String name, Geometry boundary) {
            this.name = name;
            this.boundary = boundary;
            this.preparedBoundary = PreparedGeometryFactory.prepare(boundary);
        }

        private String name() {
            return name;
        }

        private Geometry boundary() {
            return boundary;
        }

        private boolean contains(Point location) {
            return preparedBoundary.covers(location);
        }

        private boolean contains(Geometry location) {
            return preparedBoundary.covers(location);
        }
    }
}
