/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.junit.jupiter.api.Test;

public class XmlAdapterWrapperTest {
    @Test
    void adaptsCollectionElementsWhenReadingAndWritingXml() throws Exception {
        try (ProviderSession session = new ProviderSession()) {
            Label[] labels = session.read(Label[].class, Label[].class, """
                    <collection>
                      <label><text>COMPASS</text></label>
                      <label><text>MAP</text></label>
                    </collection>
                    """);

            assertThat(labels).hasSize(2);
            assertThat(labels[0].text).isEqualTo("compass");
            assertThat(labels[1].text).isEqualTo("map");

            String xml = session.write(labels, Label[].class, Label[].class);
            assertThat(xml).contains("<text>COMPASS</text>", "<text>MAP</text>");
            Label[] restored = session.read(Label[].class, Label[].class, xml);
            assertThat(restored).extracting(label -> label.text).containsExactly("compass", "map");
        }
    }

    @XmlJavaTypeAdapter(LabelAdapter.class)
    public static class Label {
        final String text;

        public Label(String text) {
            this.text = text;
        }
    }

    @XmlRootElement(name = "label")
    public static class WireLabel {
        public String text;
    }

    public static class LabelAdapter extends XmlAdapter<WireLabel, Label> {
        @Override
        public Label unmarshal(WireLabel value) {
            return new Label(value.text.toLowerCase(Locale.ROOT));
        }

        @Override
        public WireLabel marshal(Label value) {
            WireLabel wire = new WireLabel();
            wire.text = value.text.toUpperCase(Locale.ROOT);
            return wire;
        }
    }
}
