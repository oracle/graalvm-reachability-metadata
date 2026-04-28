/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.ElementSelectors;
import org.xmlunit.transform.Transformation;
import org.xmlunit.util.Convert;
import org.xmlunit.util.DocumentBuilderFactoryConfigurer;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationProblem;
import org.xmlunit.validation.ValidationResult;
import org.xmlunit.validation.Validator;
import org.xmlunit.xpath.JAXPXPathEngine;
import org.xmlunit.xpath.XPathEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Xmlunit_coreTest {
    private static final String BOOKS_CONTROL = """
            <catalog>
                <book isbn="978-0134685991">
                    <title>Effective Java</title>
                    <author>Joshua Bloch</author>
                </book>
                <book isbn="978-1617294945">
                    <title>Java Concurrency in Practice</title>
                    <author>Brian Goetz</author>
                </book>
            </catalog>
            """;

    private static final String BOOKS_REORDERED = """
            <catalog>
                <book isbn="978-1617294945">
                    <title>Java Concurrency in Practice</title><author>Brian Goetz</author>
                </book>
                <book isbn="978-0134685991">
                    <title>Effective Java</title><author>Joshua Bloch</author>
                </book>
            </catalog>
            """;

    @Test
    void similarDiffMatchesRepeatedElementsIndependentOfOrderAndWhitespace() {
        Diff diff = DiffBuilder.compare(BOOKS_CONTROL)
                .withTest(BOOKS_REORDERED)
                .ignoreWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndAttributes("isbn")))
                .checkForSimilar()
                .build();

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void nodeFiltersAttributeFiltersAndEvaluatorsCanModelDomainSpecificEquality() {
        String control = """
                <invoice generatedAt="2024-01-01T00:00:00Z" version="1">
                    <line sku="A-1" quantity="2"/>
                    <audit>created by nightly import</audit>
                </invoice>
                """;
        String test = """
                <invoice generatedAt="2024-02-15T08:30:00Z" version="2">
                    <line sku="A-1" quantity="2"/>
                    <audit>created interactively</audit>
                </invoice>
                """;

        Diff diff = DiffBuilder.compare(control)
                .withTest(test)
                .ignoreWhitespace()
                .withAttributeFilter(attribute -> !"generatedAt".equals(attribute.getName()))
                .withNodeFilter(node -> node.getNodeType() != Node.ELEMENT_NODE || !"audit".equals(node.getNodeName()))
                .withDifferenceEvaluator(DifferenceEvaluators.downgradeDifferencesToSimilar(ComparisonType.ATTR_VALUE))
                .checkForSimilar()
                .build();

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void identicalDiffExposesStructuredDifferenceDetailsForChangedText() {
        String control = """
                <order>
                    <customer>Ada Lovelace</customer>
                    <total currency="EUR">42.00</total>
                </order>
                """;
        String test = """
                <order>
                    <customer>Ada Lovelace</customer>
                    <total currency="EUR">43.50</total>
                </order>
                """;

        Diff diff = DiffBuilder.compare(control)
                .withTest(test)
                .ignoreWhitespace()
                .checkForIdentical()
                .build();

        List<Difference> differences = asList(diff.getDifferences());
        assertTrue(diff.hasDifferences());
        assertEquals(1, differences.size(), diff::fullDescription);
        Difference difference = differences.get(0);
        assertEquals(ComparisonResult.DIFFERENT, difference.getResult());
        assertEquals(ComparisonType.TEXT_VALUE, difference.getComparison().getType());
        assertEquals("42.00", difference.getComparison().getControlDetails().getValue());
        assertEquals("43.50", difference.getComparison().getTestDetails().getValue());
        assertTrue(difference.getComparison().getControlDetails().getXPath().contains("total"));
    }

    @Test
    void xpathEngineEvaluatesNamespacedExpressionsAndSelectsNodes() {
        String xml = """
                <store xmlns="urn:books">
                    <book id="b1"><title>Domain-Driven Design</title><price>54.95</price></book>
                    <book id="b2"><title>Refactoring</title><price>47.50</price></book>
                    <magazine id="m1"><title>JVM Weekly</title><price>6.00</price></magazine>
                </store>
                """;
        XPathEngine xpath = new JAXPXPathEngine();
        Map<String, String> namespaces = new HashMap<>();
        namespaces.put("bk", "urn:books");
        xpath.setNamespaceContext(namespaces);

        String firstBookTitle = xpath.evaluate(
                "string(/bk:store/bk:book[@id='b1']/bk:title)", Input.fromString(xml).build());
        List<Node> expensiveBooks = asList(xpath.selectNodes(
                "/bk:store/bk:book[bk:price > 50]", Input.fromString(xml).build()));

        assertEquals("Domain-Driven Design", firstBookTitle);
        assertEquals(1, expensiveBooks.size());
        assertEquals("book", expensiveBooks.get(0).getLocalName());
        assertEquals("b1", expensiveBooks.get(0).getAttributes().getNamedItem("id").getNodeValue());
    }

    @Test
    void validatorChecksXmlSchemaAndCollectsProblemsForInvalidDocuments() {
        String schema = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                    <xs:element name="purchaseOrder">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="item" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="name" type="xs:string"/>
                                            <xs:element name="quantity">
                                                <xs:simpleType>
                                                    <xs:restriction base="xs:int">
                                                        <xs:minInclusive value="1"/>
                                                    </xs:restriction>
                                                </xs:simpleType>
                                            </xs:element>
                                        </xs:sequence>
                                        <xs:attribute name="sku" type="xs:string" use="required"/>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;
        String validOrder = """
                <purchaseOrder>
                    <item sku="A-1"><name>Keyboard</name><quantity>2</quantity></item>
                </purchaseOrder>
                """;
        String invalidOrder = """
                <purchaseOrder>
                    <item><name>Mouse</name><quantity>0</quantity></item>
                </purchaseOrder>
                """;
        Validator validator = Validator.forLanguage(Languages.W3C_XML_SCHEMA_NS_URI);
        validator.setSchemaSource(Input.fromString(schema).build());

        ValidationResult validResult = validator.validateInstance(Input.fromString(validOrder).build());
        ValidationResult invalidResult = validator.validateInstance(Input.fromString(invalidOrder).build());

        assertTrue(validResult.isValid(), () -> problemMessages(validResult.getProblems()));
        assertFalse(invalidResult.isValid());
        List<ValidationProblem> problems = asList(invalidResult.getProblems());
        assertFalse(problems.isEmpty());
        String invalidProblemMessages = problemMessages(problems);
        assertTrue(invalidProblemMessages.contains("quantity") || invalidProblemMessages.contains("sku"));
    }

    @Test
    void documentBuilderFactoryConfigurerPreservesEntityReferencesWhenConvertingSources() {
        String xml = """
                <!DOCTYPE message [<!ENTITY writer "Ada Lovelace">]>
                <message>Hello &writer;</message>
                """;
        DocumentBuilderFactory factory = DocumentBuilderFactoryConfigurer.builder()
                .withExpandEntityReferences(false)
                .build()
                .configure(DocumentBuilderFactory.newInstance());

        Document document = Convert.toDocument(Input.fromString(xml).build(), factory);
        Node message = document.getDocumentElement();

        assertEquals("message", message.getNodeName());
        assertEquals(2, message.getChildNodes().getLength());
        assertEquals(Node.TEXT_NODE, message.getChildNodes().item(0).getNodeType());
        assertEquals("Hello ", message.getChildNodes().item(0).getNodeValue());
        assertEquals(Node.ENTITY_REFERENCE_NODE, message.getChildNodes().item(1).getNodeType());
        assertEquals("writer", message.getChildNodes().item(1).getNodeName());
    }

    @Test
    void transformationAppliesStylesheetParametersAndCanBeQueriedAsDocument() {
        String source = """
                <inventory>
                    <item sku="A-1"><name>Keyboard</name><stock>8</stock></item>
                    <item sku="B-2"><name>Mouse</name><stock>3</stock></item>
                    <item sku="C-3"><name>Monitor</name><stock>12</stock></item>
                </inventory>
                """;
        String stylesheet = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:param name="minimum"/>
                    <xsl:output method="xml" omit-xml-declaration="yes"/>
                    <xsl:template match="/inventory">
                        <restock>
                            <xsl:for-each select="item[number(stock) &lt; $minimum]">
                                <product sku="{@sku}"><xsl:value-of select="name"/></product>
                            </xsl:for-each>
                        </restock>
                    </xsl:template>
                </xsl:stylesheet>
                """;
        Transformation transformation = new Transformation(Input.fromString(source).build());
        transformation.setStylesheet(Input.fromString(stylesheet).build());
        transformation.addParameter("minimum", 10);

        Document document = transformation.transformToDocument();
        XPathEngine xpath = new JAXPXPathEngine();
        String productCount = xpath.evaluate("count(/restock/product)", document);
        String firstSku = xpath.evaluate("string(/restock/product[1]/@sku)", document);
        String secondProductName = xpath.evaluate("string(/restock/product[2])", document);

        assertNotNull(document.getDocumentElement());
        assertEquals("restock", document.getDocumentElement().getNodeName());
        assertEquals("2", productCount);
        assertEquals("A-1", firstSku);
        assertEquals("Mouse", secondProductName);
    }

    private static <T> List<T> asList(Iterable<T> iterable) {
        List<T> values = new ArrayList<>();
        for (T value : iterable) {
            values.add(value);
        }
        return values;
    }

    private static String problemMessages(Iterable<ValidationProblem> problems) {
        StringBuilder messages = new StringBuilder();
        for (ValidationProblem problem : problems) {
            if (messages.length() > 0) {
                messages.append('\n');
            }
            messages.append(problem.getMessage());
        }
        return messages.toString();
    }
}
