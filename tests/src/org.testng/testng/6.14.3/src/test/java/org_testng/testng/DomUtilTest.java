/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;
import org.testng.xml.dom.DomUtil;
import org.w3c.dom.Document;

public class DomUtilTest {
    @Test
    void populatesSuiteTestAndClassAttributesThroughDomReflectionSetters() throws Exception {
        String xml = """
                <suite name="dom-suite" verbose="7" junit="true" thread-count="8">
                    <parameter name="suite-parameter" value="suite-value"/>
                    <test name="dom-test" verbose="3" junit="true" thread-count="2">
                        <parameter name="test-parameter" value="test-value"/>
                        <classes>
                            <class name="java.lang.String" index="4"/>
                        </classes>
                    </test>
                    <suite-files>
                        <suite-file path="nested-suite.xml"/>
                    </suite-files>
                </suite>
                """;
        XmlSuite suite = new XmlSuite();

        new DomUtil(parse(xml)).populate(suite);

        assertThat(suite.getName()).isEqualTo("dom-suite");
        assertThat(suite.getVerbose()).isEqualTo(7);
        assertThat(suite.isJUnit()).isTrue();
        assertThat(suite.getThreadCount()).isEqualTo(8);
        assertThat(suite.getParameters()).containsEntry("suite-parameter", "suite-value");
        assertThat(suite.getSuiteFiles()).containsExactly("nested-suite.xml");

        assertThat(suite.getTests()).hasSize(1);
        XmlTest test = suite.getTests().get(0);
        assertThat(test.getName()).isEqualTo("dom-test");
        assertThat(test.getVerbose()).isEqualTo(3);
        assertThat(test.isJUnit()).isTrue();
        assertThat(test.getThreadCount()).isEqualTo(2);
        assertThat(test.getAllParameters()).containsEntry("test-parameter", "test-value");

        assertThat(test.getXmlClasses()).hasSize(1);
        XmlClass xmlClass = test.getXmlClasses().get(0);
        assertThat(xmlClass.getName()).isEqualTo("java.lang.String");
        assertThat(xmlClass.getIndex()).isEqualTo(4);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
