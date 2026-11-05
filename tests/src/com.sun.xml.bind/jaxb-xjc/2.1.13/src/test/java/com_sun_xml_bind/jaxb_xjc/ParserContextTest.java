/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_xjc;

import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.parser.XSOMParser;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserContextTest {
    @Test
    void resolvesBuiltinDatatypesAndSchemaReferences() throws Exception {
        XSOMParser parser = new XSOMParser();
        InputSource schema = new InputSource(new StringReader("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:inventory" targetNamespace="urn:inventory">
                  <xs:simpleType name="StockCode">
                    <xs:restriction base="xs:string">
                      <xs:maxLength value="12"/>
                    </xs:restriction>
                  </xs:simpleType>
                  <xs:complexType name="StockItem">
                    <xs:sequence>
                      <xs:element name="code" type="tns:StockCode"/>
                    </xs:sequence>
                    <xs:attribute name="quantity" type="xs:int" use="required"/>
                  </xs:complexType>
                  <xs:element name="stock" type="tns:StockItem"/>
                </xs:schema>
                """));
        schema.setSystemId("urn:inventory:schema");
        parser.parse(schema);

        XSSchemaSet result = parser.getResult();
        assertThat(result).isNotNull();
        XSSimpleType stockCode = result.getSimpleType("urn:inventory", "StockCode");
        assertThat(stockCode).isNotNull();
        assertThat(stockCode.getBaseType().getName()).isEqualTo("string");
        assertThat(stockCode.getFacet(XSFacet.FACET_MAXLENGTH).getValue().value).isEqualTo("12");
        XSComplexType item = result.getComplexType("urn:inventory", "StockItem");
        assertThat(result.getElementDecl("urn:inventory", "stock").getType()).isSameAs(item);
        assertThat(item.getAttributeUse("", "quantity").isRequired()).isTrue();
        assertThat(item.getAttributeUse("", "quantity").getDecl().getType())
                .isSameAs(result.getSimpleType("http://www.w3.org/2001/XMLSchema", "int"));
        assertThat(item.getContentType().asParticle().getTerm().asModelGroup().getChild(0)
                .getTerm().asElementDecl().getType()).isSameAs(stockCode);
    }
}
