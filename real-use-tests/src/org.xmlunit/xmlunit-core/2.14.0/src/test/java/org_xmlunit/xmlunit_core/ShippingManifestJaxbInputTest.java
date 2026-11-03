/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises XMLUnit's JAXB input subsystem to verify a shipping manifest before sending it to a carrier.
public class ShippingManifestJaxbInputTest {
    @Test
    @Timeout(60)
    public void acceptsManifestWhenJaxbObjectMatchesCarrierContract() {
        ShippingManifest manifest = new ShippingManifest(
                "SHIP-2048",
                "Casey Morgan",
                List.of(new Parcel("BOX-1", "42 Market Street", "90210")));

        Diff comparison = DiffBuilder.compare(Input.fromString("""
                <shippingManifest>
                    <shipmentId>SHIP-2048</shipmentId>
                    <recipient>Casey Morgan</recipient>
                    <parcel>
                        <trackingId>BOX-1</trackingId>
                        <address>42 Market Street</address>
                        <postalCode>90210</postalCode>
                    </parcel>
                </shippingManifest>
                """)).withTest(Input.fromJaxb(manifest))
                .ignoreWhitespace()
                .checkForIdentical()
                .build();

        assertFalse(comparison.hasDifferences(), comparison::fullDescription);
    }

    @XmlRootElement(name = "shippingManifest")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ShippingManifest {
        private String shipmentId;
        private String recipient;
        @XmlElement(name = "parcel")
        private List<Parcel> parcels;

        public ShippingManifest() {
        }

        private ShippingManifest(String shipmentId, String recipient, List<Parcel> parcels) {
            this.shipmentId = shipmentId;
            this.recipient = recipient;
            this.parcels = parcels;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Parcel {
        private String trackingId;
        private String address;
        private String postalCode;

        public Parcel() {
        }

        private Parcel(String trackingId, String address, String postalCode) {
            this.trackingId = trackingId;
            this.address = address;
            this.postalCode = postalCode;
        }
    }
}
