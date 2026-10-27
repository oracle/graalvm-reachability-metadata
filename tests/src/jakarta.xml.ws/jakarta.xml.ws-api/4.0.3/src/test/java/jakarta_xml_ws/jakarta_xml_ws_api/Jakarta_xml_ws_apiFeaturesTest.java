/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_ws.jakarta_xml_ws_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.xml.ws.RespectBindingFeature;
import jakarta.xml.ws.soap.AddressingFeature;
import jakarta.xml.ws.soap.MTOMFeature;
import org.junit.jupiter.api.Test;

public class Jakarta_xml_ws_apiFeaturesTest {
    @Test
    void configuresAddressingFeature() {
        AddressingFeature feature = new AddressingFeature(true, true);

        assertTrue(feature.isEnabled());
        assertTrue(feature.isRequired());
        assertEquals(AddressingFeature.ID, feature.getID());
    }

    @Test
    void configuresMtomFeatureThreshold() {
        MTOMFeature feature = new MTOMFeature(4096);

        assertTrue(feature.isEnabled());
        assertEquals(4096, feature.getThreshold());
        assertEquals(MTOMFeature.ID, feature.getID());
    }

    @Test
    void enablesRespectBindingFeature() {
        RespectBindingFeature feature = new RespectBindingFeature();

        assertTrue(feature.isEnabled());
        assertEquals(RespectBindingFeature.ID, feature.getID());
    }
}
