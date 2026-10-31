/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.LinkedList;

import jakarta.ws.rs.core.GenericType;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.junit.jupiter.api.Test;

public class CollectionProviderTest {
    private static final String XML = """
            <collection>
              <entry><name>compass</name></entry>
              <entry><name>map</name></entry>
            </collection>
            """;

    @Test
    void readsWrappedXmlIntoAnArrayOfTheElementType() throws Exception {
        try (ProviderSession session = new ProviderSession()) {
            Entry[] entries = session.read(Entry[].class, Entry[].class, XML);

            assertThat(entries).hasSize(2);
            assertThat(entries[0].name).isEqualTo("compass");
            assertThat(entries[1].name).isEqualTo("map");
            String written = session.write(entries, Entry[].class, Entry[].class);
            Entry[] restored = session.read(Entry[].class, Entry[].class, written);
            assertThat(restored).extracting(entry -> entry.name).containsExactly("compass", "map");
        }
    }

    @Test
    void instantiatesTheRequestedConcreteCollection() throws Exception {
        Type genericType = new GenericType<LinkedList<Entry>>() {
        }.getType();
        try (ProviderSession session = new ProviderSession()) {
            LinkedList<?> entries = session.read(LinkedList.class, genericType, XML);

            assertThat(entries).isExactlyInstanceOf(LinkedList.class).hasSize(2);
            assertThat(((Entry) entries.getFirst()).name).isEqualTo("compass");
            assertThat(((Entry) entries.getLast()).name).isEqualTo("map");
        }
    }

    @XmlRootElement(name = "entry")
    public static class Entry {
        public String name;
    }
}
