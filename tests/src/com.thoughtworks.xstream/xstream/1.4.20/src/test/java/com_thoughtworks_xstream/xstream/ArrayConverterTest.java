/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConverterTest {
    @Test
    void deserializesTopLevelStringArrayWithDefaultArrayConverter() {
        XStream xstream = new XStream();
        String[] values = new String[]{"alpha", "beta", "gamma"};

        String xml = xstream.toXML(values);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("<string>alpha</string>", "<string>beta</string>", "<string>gamma</string>");
        assertThat(restored).isInstanceOf(String[].class);
        assertThat((String[])restored).containsExactly(values);
    }
}
