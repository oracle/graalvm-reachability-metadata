/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import javax.activation.ActivationDataFlavor;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivationDataFlavorConverterTest {
    @Test
    void serializesAndDeserializesActivationDataFlavor() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{ActivationDataFlavor.class});
        ActivationDataFlavor flavor = new ActivationDataFlavor(String.class, "text/plain", "plain text");

        String xml = xstream.toXML(flavor);

        assertThat(xml).contains("activation-data-flavor");
        Object restored = xstream.fromXML(xml);
        assertThat(restored).isInstanceOf(ActivationDataFlavor.class);
        ActivationDataFlavor restoredFlavor = (ActivationDataFlavor)restored;
        assertThat(restoredFlavor.getRepresentationClass()).isEqualTo(String.class);
        assertThat(restoredFlavor.isMimeTypeEqual("text/plain")).isTrue();
        assertThat(restoredFlavor.getHumanPresentableName()).isEqualTo("plain text");
    }
}
