/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.TransformerFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StyleNodeFactoryTest {
    @Test
    void stylesheetCompilationCreatesAbsentExtensionElementForUnknownExtensionInstruction() throws Exception {
        String stylesheet = """
                <xsl:stylesheet version="3.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:ext="http://example.com/saxon-test/extensions"
                    extension-element-prefixes="ext">
                    <xsl:output method="xml" omit-xml-declaration="yes"/>
                    <xsl:template match="/">
                        <result>
                            <ext:emit>
                                <xsl:fallback>
                                    <fallback>extension unavailable</fallback>
                                </xsl:fallback>
                            </ext:emit>
                        </result>
                    </xsl:template>
                </xsl:stylesheet>
                """;
        TransformerFactory factory = new TransformerFactoryImpl();
        Transformer transformer = factory.newTransformer(new StreamSource(new StringReader(stylesheet)));
        StringWriter output = new StringWriter();

        transformer.transform(
                new StreamSource(new StringReader("<input/>")),
                new StreamResult(output));

        assertThat(output.toString())
                .contains("<result>")
                .contains("<fallback>extension unavailable</fallback>");
    }
}
