/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_xjc;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.writer.FileCodeWriter;
import com.sun.tools.xjc.api.Mapping;
import com.sun.tools.xjc.api.S2JJAXBModel;
import com.sun.tools.xjc.api.SchemaCompiler;
import com.sun.tools.xjc.api.XJC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaCompilerImplTest {
    @TempDir
    Path output;

    @Test
    void generatesBeansAndFactoryFromSchema() throws Exception {
        SchemaCompiler compiler = XJC.createSchemaCompiler();
        compiler.forcePackageName("generated.orders");
        InputSource schema = new InputSource(new StringReader("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:orders" elementFormDefault="qualified">
                  <xs:element name="order">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="number" type="xs:int"/>
                        <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """));
        schema.setSystemId("urn:orders:schema");
        compiler.parseSchema(schema);

        S2JJAXBModel model = compiler.bind();
        assertThat(model).isNotNull();
        Mapping order = model.get(new QName("urn:orders", "order"));
        assertThat(order).isNotNull();
        assertThat(order.getType().getTypeClass().fullName()).isEqualTo("generated.orders.Order");
        assertThat(model.getAllObjectFactories()).extracting(factory -> factory.fullName())
                .containsExactly("generated.orders.ObjectFactory");

        JCodeModel code = model.generateCode(null, null);
        code.build(new FileCodeWriter(output.toFile()));
        String bean = Files.readString(output.resolve("generated/orders/Order.java"));
        assertThat(bean).contains("class Order", "int getNumber()", "void setNumber(int value)",
                "List<String> getItem()", "@XmlRootElement(name = \"order\")");
        String factory = Files.readString(output.resolve("generated/orders/ObjectFactory.java"));
        assertThat(factory).contains("@XmlRegistry", "Order createOrder()", "return new Order()");
        assertThat(Files.readString(output.resolve("generated/orders/package-info.java")))
                .contains("urn:orders", "XmlNsForm.QUALIFIED");
    }
}
