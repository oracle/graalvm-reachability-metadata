/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import org.apache.xalan.processor.TransformerFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.view.xslt.XsltView;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XsltViewTest {
    @TempDir
    Path directory;

    @Test
    void rendersXmlWithConfiguredTransformerFactoryAndModelParameters() throws Exception {
        Path stylesheet = directory.resolve("greeting.xsl");
        Files.writeString(stylesheet, """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="text" media-type="text/plain" encoding="UTF-8"/>
                    <xsl:param name="greeting"/>
                    <xsl:template match="/">
                        <xsl:value-of select="$greeting"/>
                        <xsl:text>, </xsl:text>
                        <xsl:value-of select="customer/name"/>
                        <xsl:text>!</xsl:text>
                    </xsl:template>
                </xsl:stylesheet>
                """);

        MockServletContext servletContext = new MockServletContext();
        try (GenericWebApplicationContext context = new GenericWebApplicationContext()) {
            context.setServletContext(servletContext);
            context.refresh();

            XsltView view = new XsltView();
            view.setUrl(stylesheet.toUri().toString());
            view.setTransformerFactoryClass(TransformerFactoryImpl.class);
            view.setSourceKey("document");
            view.setIndent(false);
            view.setApplicationContext(context);

            MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            view.render(Map.of("document", new StreamSource(new StringReader("<customer><name>Ada</name></customer>")),
                    "greeting", "Welcome"), new MockHttpServletRequest(servletContext), firstResponse);
            assertEquals("Welcome, Ada!", firstResponse.getContentAsString());
            assertEquals("text/plain;charset=UTF-8", firstResponse.getContentType());

            MockHttpServletResponse secondResponse = new MockHttpServletResponse();
            view.render(Map.of("document", new StreamSource(new StringReader("<customer><name>Grace</name></customer>")),
                    "greeting", "Hello"), new MockHttpServletRequest(servletContext), secondResponse);
            assertEquals("Hello, Grace!", secondResponse.getContentAsString());
            assertEquals("text/plain;charset=UTF-8", secondResponse.getContentType());
        }
    }
}
