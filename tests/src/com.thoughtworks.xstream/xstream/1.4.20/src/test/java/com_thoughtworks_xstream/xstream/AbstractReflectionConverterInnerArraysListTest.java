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

public class AbstractReflectionConverterInnerArraysListTest {
    @Test
    void deserializesImplicitElementsIntoArrayField() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{Catalog.class, Tag.class});
        xstream.alias("catalog", Catalog.class);
        xstream.alias("tag", Tag.class);
        xstream.addImplicitArray(Catalog.class, "tags", "tag");

        Catalog catalog = (Catalog)xstream.fromXML("""
                <catalog>
                  <tag>
                    <name>alpha</name>
                  </tag>
                  <tag>
                    <name>beta</name>
                  </tag>
                </catalog>
                """);

        assertThat(catalog.tags).hasSize(2);
        assertThat(catalog.tags[0].name).isEqualTo("alpha");
        assertThat(catalog.tags[1].name).isEqualTo("beta");
    }

    public static final class Catalog {
        public Tag[] tags;
    }

    public static final class Tag {
        public String name;
    }
}
