/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionalHandlerFactoryTest {
    @Test
    void readsAndWritesXmlQualifiedNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        QName name = mapper.readValue("\"{urn:inventory}item\"", QName.class);
        assertEquals("urn:inventory", name.getNamespaceURI());
        assertEquals("item", name.getLocalPart());
        assertEquals("\"{urn:inventory}item\"", mapper.writeValueAsString(name));
    }
}
