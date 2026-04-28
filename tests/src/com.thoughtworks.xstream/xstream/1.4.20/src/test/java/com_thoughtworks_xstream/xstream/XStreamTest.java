/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XStreamTest {
    @Test
    void serializesAndDeserializesCollectionsWithDefaultDynamicMappers() {
        XStream xstream = new XStream();
        List<Object> values = new ArrayList<>();
        values.add("alpha");
        values.add(42);
        values.add(LocalDate.of(2024, 4, 28));
        values.add(YearMonth.of(2024, 4));
        values.add(Optional.of("present"));

        String xml = xstream.toXML(values);

        Object restored = xstream.fromXML(xml);
        assertThat(restored).isInstanceOf(List.class);
        assertThat(restored).isEqualTo(values);
    }

    @Test
    void serializesAndDeserializesStringLists() {
        XStream xstream = new XStream();
        List<String> records = new ArrayList<>();
        records.add("created");
        records.add("processed");

        String xml = xstream.toXML(records);

        assertThat(xml).contains("<string>created</string>");
        assertThat(xml).contains("<string>processed</string>");
        assertThat(xstream.fromXML(xml)).isEqualTo(records);
    }
}
