/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.extended.NamedArrayConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamedArrayConverterTest {
    @Test
    void roundTripsLocalStringArrayWithNamedItemsAndNullValues() {
        XStream xstream = configuredXStream();
        NamedArrayHolder original = new NamedArrayHolder(new String[]{"alpha", null, "omega"});

        String xml = xstream.toXML(original);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("<item>alpha</item>", "<item>omega</item>");
        assertThat(restored).isInstanceOf(NamedArrayHolder.class);
        assertThat(((NamedArrayHolder)restored).items).containsExactly("alpha", null, "omega");
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{NamedArrayHolder.class, String[].class});
        xstream.alias("named-array-holder", NamedArrayHolder.class);
        xstream.registerLocalConverter(
            NamedArrayHolder.class,
            "items",
            new NamedArrayConverter(String[].class, xstream.getMapper(), "item"));
        return xstream;
    }

    public static final class NamedArrayHolder {
        public String[] items;

        public NamedArrayHolder() {
        }

        NamedArrayHolder(String[] items) {
            this.items = items;
        }
    }
}
