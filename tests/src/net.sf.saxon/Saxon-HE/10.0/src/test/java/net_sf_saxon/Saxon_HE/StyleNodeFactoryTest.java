/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.TransformerFactoryImpl;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

public class StyleNodeFactoryTest {
    @Test
    void usesFallbackForUnknownExtensionInstruction() throws Exception {
        String stylesheet = """
                <xsl:stylesheet version="3.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:ext="http://example.com/saxon-test-extension"
                    extension-element-prefixes="ext">
                    <xsl:output method="xml" omit-xml-declaration="yes"/>
                    <xsl:template match="/">
                        <result>
                            <ext:instruction>
                                <xsl:fallback>
                                    <fallback>used</fallback>
                                </xsl:fallback>
                            </ext:instruction>
                        </result>
                    </xsl:template>
                </xsl:stylesheet>
                """;
        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        Templates templates = factory.newTemplates(new StreamSource(new StringReader(stylesheet)));
        Transformer transformer = templates.newTransformer();
        StringWriter output = new StringWriter();

        transformer.transform(
                new StreamSource(new StringReader("<input/>")),
                new StreamResult(output));

        assertThat(output).hasToString("<result><fallback>used</fallback></result>");
    }
}
