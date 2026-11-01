/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_xjc;

import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.parser.XSOMParser;
import com.sun.xml.xsom.util.NameGetter;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class NameGetterTest {
    @Test
    void labelsSchemaComponentsInTheRequestedLocale() throws Exception {
        XSOMParser parser = new XSOMParser();
        InputSource input = new InputSource(new StringReader("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:labels">
                  <xs:element name="label" type="xs:string"/>
                </xs:schema>
                """));
        input.setSystemId("urn:labels:schema");
        parser.parse(input);
        XSSchemaSet schemas = parser.getResult();
        assertThat(schemas).isNotNull();
        NameGetter names = new NameGetter(Locale.ENGLISH);
        assertThat(schemas.getSchema("urn:labels").apply(names)).isEqualTo("schema");
        assertThat(schemas.getElementDecl("urn:labels", "label").apply(names)).isEqualTo("element declaration");
        assertThat(schemas.getElementDecl("urn:labels", "label").getType().apply(names)).isEqualTo("simple type");
    }
}
