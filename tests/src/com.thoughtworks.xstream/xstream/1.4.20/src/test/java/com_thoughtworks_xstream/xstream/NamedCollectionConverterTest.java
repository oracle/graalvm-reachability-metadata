/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.ArrayList;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.extended.NamedCollectionConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamedCollectionConverterTest {
    @Test
    void roundTripsLocalCollectionWithNamedItemsAndNullValues() {
        XStream xstream = configuredXStream();
        NamedCollectionHolder original = new NamedCollectionHolder();
        original.items.add("alpha");
        original.items.add(null);
        original.items.add("omega");

        String xml = xstream.toXML(original);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("<entry>alpha</entry>", "<entry class=\"null\"", "<entry>omega</entry>");
        assertThat(restored).isInstanceOf(NamedCollectionHolder.class);
        assertThat(((NamedCollectionHolder)restored).items).containsExactly("alpha", null, "omega");
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{NamedCollectionHolder.class, ArrayList.class});
        xstream.alias("named-collection-holder", NamedCollectionHolder.class);
        xstream.registerLocalConverter(
            NamedCollectionHolder.class,
            "items",
            new NamedCollectionConverter(ArrayList.class, xstream.getMapper(), "entry", String.class));
        return xstream;
    }

    public static final class NamedCollectionHolder {
        public ArrayList<String> items = new ArrayList<>();
    }
}
