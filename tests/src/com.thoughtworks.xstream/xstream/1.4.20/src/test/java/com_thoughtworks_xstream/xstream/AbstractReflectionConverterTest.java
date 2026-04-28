/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.LinkedHashMap;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractReflectionConverterTest {
    @Test
    void writesNullValueInImplicitMapEntryAsNullNode() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{ImplicitMapHolder.class});
        xstream.alias("holder", ImplicitMapHolder.class);
        xstream.addImplicitMap(ImplicitMapHolder.class, "entries", "entry", Object.class, null);

        ImplicitMapHolder holder = new ImplicitMapHolder();
        holder.entries.put("present", null);

        String xml = xstream.toXML(holder);

        assertThat(xml).contains("<entry>", "<string>present</string>", "<null/>");
    }

    public static final class ImplicitMapHolder {
        public final LinkedHashMap<String, Object> entries = new LinkedHashMap<>();
    }
}
