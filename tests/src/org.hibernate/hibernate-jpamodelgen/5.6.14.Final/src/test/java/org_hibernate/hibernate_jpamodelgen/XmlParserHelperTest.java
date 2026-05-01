/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_jpamodelgen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.xml.validation.Schema;

import org.hibernate.jpamodelgen.util.xml.XmlParserHelper;
import org.hibernate.jpamodelgen.util.xml.XmlParsingException;
import org.junit.jupiter.api.Test;

public class XmlParserHelperTest {
    private static final String TEST_SCHEMA_RESOURCE = "org_hibernate/hibernate_jpamodelgen/xml-parser-helper-test.xsd";

    @Test
    void loadsSchemaFromClassLoaderResourceAndCachesIt() throws XmlParsingException {
        XmlParserHelper helper = new XmlParserHelper(null);

        Schema schema = helper.getSchema(TEST_SCHEMA_RESOURCE);
        Schema cachedSchema = helper.getSchema(TEST_SCHEMA_RESOURCE);

        assertNotNull(schema);
        assertSame(schema, cachedSchema);
    }
}
