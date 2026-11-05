/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELProcessor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resolves indexed lists and arrays with Jakarta Expression Language's collection resolver
 * subsystem. A logistics application can inspect and update a shipment manifest through
 * expressions while keeping its parcel model in ordinary application objects.
 */
public class ManifestCollectionExpressionTest {

    @Test
    @Timeout(60)
    public void readsAndUpdatesIndexedManifestCollections() {
        Manifest manifest = new Manifest(
                new ArrayList<>(List.of(
                        new Parcel("PKG-1", "READY"),
                        new Parcel("PKG-2", "IN_TRANSIT"))),
                new String[] {"EU", "US", "APAC"});
        ELProcessor expressions = new ELProcessor();
        expressions.defineBean("manifest", manifest);

        String secondParcel = expressions.eval("manifest.parcels[1].trackingCode");
        String destination = expressions.eval("manifest.destinations[2]");
        Number parcelCount = expressions.eval("manifest.parcels.size()");
        expressions.setValue("manifest.parcels[0].status", "DELIVERED");
        String firstStatus = expressions.eval("manifest.parcels[0].status");

        assertEquals("PKG-2", secondParcel);
        assertEquals("APAC", destination);
        assertEquals(2, parcelCount.intValue());
        assertEquals("DELIVERED", firstStatus);
        assertEquals("DELIVERED", manifest.getParcels().get(0).getStatus());
    }

    /** Shipment data exposed to the manifest expressions by the logistics application. */
    public static final class Manifest {
        private final List<Parcel> parcels;
        private final String[] destinations;

        public Manifest(List<Parcel> parcels, String[] destinations) {
            this.parcels = parcels;
            this.destinations = destinations;
        }

        public List<Parcel> getParcels() {
            return parcels;
        }

        public String[] getDestinations() {
            return destinations;
        }
    }

    /** Parcel record edited through the application's expression-backed manifest. */
    public static final class Parcel {
        private final String trackingCode;
        private String status;

        public Parcel(String trackingCode, String status) {
            this.trackingCode = trackingCode;
            this.status = status;
        }

        public String getTrackingCode() {
            return trackingCode;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
