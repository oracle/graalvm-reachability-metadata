/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.LinkedHashMap;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.extended.NamedMapConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamedMapConverterTest {
    @Test
    void roundTripsLocalMapWithNamedEntriesAndNullValues() {
        XStream xstream = configuredXStream();
        NamedMapHolder original = new NamedMapHolder();
        original.values.put("alpha", "one");
        original.values.put("empty-value", null);
        original.values.put(null, "empty-key");

        String xml = xstream.toXML(original);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains(
            "<key>alpha</key>",
            "<value>one</value>",
            "<value class=\"null\"",
            "<key class=\"null\"");
        assertThat(restored).isInstanceOf(NamedMapHolder.class);
        assertThat(((NamedMapHolder)restored).values)
            .containsEntry("alpha", "one")
            .containsEntry("empty-value", null)
            .containsEntry(null, "empty-key");
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{NamedMapHolder.class, LinkedHashMap.class});
        xstream.alias("named-map-holder", NamedMapHolder.class);
        xstream.registerLocalConverter(
            NamedMapHolder.class,
            "values",
            new NamedMapConverter(
                LinkedHashMap.class,
                xstream.getMapper(),
                "entry",
                "key",
                String.class,
                "value",
                String.class));
        return xstream;
    }

    public static final class NamedMapHolder {
        public LinkedHashMap<String, String> values = new LinkedHashMap<>();
    }
}
