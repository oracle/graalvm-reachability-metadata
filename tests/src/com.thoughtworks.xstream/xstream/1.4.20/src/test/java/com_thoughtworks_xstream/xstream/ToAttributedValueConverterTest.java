/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ToAttributedValueConverterTest {
    @Test
    void roundTripsConfiguredValueFieldAsElementText() {
        XStream xstream = configuredXStream();
        AttributedMessage original = new AttributedMessage("greeting", "hello xstream");

        String xml = xstream.toXML(original);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("kind=\"greeting\"", ">hello xstream<");
        assertThat(restored).isInstanceOf(AttributedMessage.class);
        AttributedMessage restoredMessage = (AttributedMessage)restored;
        assertThat(restoredMessage.kind).isEqualTo("greeting");
        assertThat(restoredMessage.text).isEqualTo("hello xstream");
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{AttributedMessage.class});
        xstream.alias("message", AttributedMessage.class);
        xstream.registerConverter(new ToAttributedValueConverter(
            AttributedMessage.class,
            xstream.getMapper(),
            xstream.getReflectionProvider(),
            xstream.getConverterLookup(),
            "text"));
        return xstream;
    }

    public static final class AttributedMessage {
        public String kind;
        public String text;

        public AttributedMessage() {
        }

        public AttributedMessage(String kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }
}
