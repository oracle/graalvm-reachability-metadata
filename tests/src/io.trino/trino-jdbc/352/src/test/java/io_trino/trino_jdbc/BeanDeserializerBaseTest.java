/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanDeserializerBaseTest {
    @Test
    void bindsInnerClassValuedField() throws Exception {
        Document document = new ObjectMapper().readValue("{\"section\":{\"title\":\"Overview\"}}", Document.class);
        assertEquals("Overview", document.section.title);
        assertEquals("document:Overview", document.section.label());
    }

    public static class Document {
        public Section section;

        public class Section {
            public String title;

            public String label() {
                return "document:" + title;
            }
        }
    }
}
