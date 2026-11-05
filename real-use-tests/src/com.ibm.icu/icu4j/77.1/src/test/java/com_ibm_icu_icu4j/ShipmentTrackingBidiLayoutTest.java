/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.Bidi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's bidirectional-text subsystem to lay out mixed-script shipment labels.
public class ShipmentTrackingBidiLayoutTest {
    @Test
    public void laysOutArabicDestinationAndTrackingNumberForALeftToRightLabel() {
        ShipmentLabel label = new ShipmentLabel();

        assertEquals("Order 123: \u0627\u0628\u062d\u0631\u0645", label.visualText("Order 123: \u0645\u0631\u062d\u0628\u0627"));
        assertEquals("Customer: 42 \u05dd\u05d5\u05dc\u05e9", label.visualText("Customer: \u05e9\u05dc\u05d5\u05dd 42"));
    }

    private static final class ShipmentLabel {
        private String visualText(String logicalText) {
            Bidi bidi = new Bidi();
            bidi.setPara(logicalText, Bidi.LTR, null);
            return bidi.writeReordered(Bidi.DO_MIRRORING);
        }
    }
}
