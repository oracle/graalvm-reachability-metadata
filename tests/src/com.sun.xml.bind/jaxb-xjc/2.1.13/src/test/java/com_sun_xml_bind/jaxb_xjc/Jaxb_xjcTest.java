/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_xjc;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.Driver;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.api.Mapping;
import com.sun.tools.xjc.api.S2JJAXBModel;
import com.sun.tools.xjc.api.SchemaCompiler;
import com.sun.tools.xjc.api.XJC;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.parser.XSOMParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class Jaxb_xjcTest {
    private static final String NO_OPTIMIZE_PROPERTY =
            "com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize";
    private static final String SCHEMA =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:tns="urn:orders"
                       xmlns:jaxb="http://java.sun.com/xml/ns/jaxb"
                       targetNamespace="urn:orders"
                       elementFormDefault="qualified"
                       jaxb:version="2.0">
              <xs:annotation>
                <xs:appinfo>
                  <jaxb:schemaBindings>
                    <jaxb:package name="generated.customized"/>
                  </jaxb:schemaBindings>
                </xs:appinfo>
              </xs:annotation>
              <xs:complexType name="OrderType">
                <xs:sequence>
                  <xs:element name="id" type="xs:string"/>
                  <xs:element name="quantity" type="xs:int"/>
                </xs:sequence>
              </xs:complexType>
              <xs:element name="order" type="tns:OrderType"/>
            </xs:schema>
            """;

    static {
        System.setProperty(NO_OPTIMIZE_PROPERTY, Boolean.TRUE.toString());
    }

    @Test
    void driverGeneratesSourcesWithCommandLineOptions(@TempDir Path tempDir) throws Exception {
        Path schema = writeSchema(tempDir);
        Path output = Files.createDirectory(tempDir.resolve("driver-output"));
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        int exitCode;

        try (PrintStream out = new PrintStream(standardOutput, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(errorOutput, true, StandardCharsets.UTF_8)) {
            exitCode =
                    Driver.run(
                            new String[] {
                                "-d",
                                output.toString(),
                                "-npa",
                                "-no-header",
                                "-p",
                                "generated.forced",
                                schema.toString()
                            },
                            out,
                            err);
        }

        assertThat(exitCode)
                .as("XJC diagnostics: %s", errorOutput.toString(StandardCharsets.UTF_8))
                .isZero();
        Path generatedPackage = output.resolve("generated/forced");
        Path orderType = generatedPackage.resolve("OrderType.java");
        assertThat(generatedPackage.resolve("ObjectFactory.java")).isRegularFile();
        assertThat(orderType).isRegularFile();
        assertThat(generatedPackage.resolve("package-info.java")).doesNotExist();

        String orderSource = Files.readString(orderType);
        assertThat(orderSource)
                .contains("package generated.forced;")
                .contains("class OrderType")
                .contains("getId()")
                .contains("setQuantity(")
                .doesNotContain("Generated on:");
    }

    @Test
    void schemaCompilerAppliesCustomizationAndBuildsCodeModel(@TempDir Path tempDir)
            throws Exception {
        Path schema = writeSchema(tempDir);
        SchemaCompiler compiler = XJC.createSchemaCompiler();
        compiler.parseSchema(new InputSource(schema.toUri().toString()));

        S2JJAXBModel model = compiler.bind();
        assertThat(model).isNotNull();

        Mapping orderMapping = model.get(new QName("urn:orders", "order"));
        assertThat(orderMapping).isNotNull();
        assertThat(orderMapping.getType().getTypeClass().fullName())
                .contains("generated.customized.OrderType");
        assertThat(model.getMappings()).hasSize(1);
        assertThat(model.getAllObjectFactories()).hasSize(1);
        assertThat(model.getAllObjectFactories().get(0).fullName())
                .isEqualTo("generated.customized.ObjectFactory");

        JCodeModel codeModel = model.generateCode(new Plugin[0], null);
        Path output = Files.createDirectory(tempDir.resolve("api-output"));
        codeModel.build(output.toFile());

        Path generatedPackage = output.resolve("generated/customized");
        Path packageInfo = generatedPackage.resolve("package-info.java");
        Path objectFactory = generatedPackage.resolve("ObjectFactory.java");
        assertThat(generatedPackage.resolve("OrderType.java")).isRegularFile();
        assertThat(packageInfo).isRegularFile();
        assertThat(objectFactory).isRegularFile();
        assertThat(Files.readString(packageInfo))
                .contains("XmlSchema")
                .contains("urn:orders")
                .contains("package generated.customized;");
        assertThat(Files.readString(objectFactory))
                .contains("@XmlElementDecl")
                .contains("namespace = \"urn:orders\"")
                .contains("name = \"order\"");
    }

    @Test
    void xsomParsesSchemaIntoQueryableComponentModel(@TempDir Path tempDir) throws Exception {
        Path schema = writeSchema(tempDir);
        XSOMParser parser = new XSOMParser();

        parser.parse(schema.toFile());

        XSSchemaSet schemaSet = parser.getResult();
        assertThat(schemaSet).isNotNull();
        XSSchema ordersSchema = schemaSet.getSchema("urn:orders");
        assertThat(ordersSchema).isNotNull();

        XSElementDecl order = ordersSchema.getElementDecl("order");
        assertThat(order).isNotNull();
        assertThat(order.getType().isComplexType()).isTrue();

        XSComplexType orderType = order.getType().asComplexType();
        assertThat(orderType.getName()).isEqualTo("OrderType");

        XSModelGroup sequence = orderType.getContentType().asParticle().getTerm().asModelGroup();
        assertThat(sequence.getCompositor()).isEqualTo(XSModelGroup.SEQUENCE);
        assertThat(sequence.getSize()).isEqualTo(2);

        XSElementDecl id = sequence.getChild(0).getTerm().asElementDecl();
        assertThat(id.getName()).isEqualTo("id");
        assertThat(id.getType().getName()).isEqualTo("string");

        XSElementDecl quantity = sequence.getChild(1).getTerm().asElementDecl();
        assertThat(quantity.getName()).isEqualTo("quantity");
        assertThat(quantity.getType().getName()).isEqualTo("int");
    }

    private static Path writeSchema(Path directory) throws IOException {
        return Files.writeString(directory.resolve("orders.xsd"), SCHEMA, StandardCharsets.UTF_8);
    }
}
