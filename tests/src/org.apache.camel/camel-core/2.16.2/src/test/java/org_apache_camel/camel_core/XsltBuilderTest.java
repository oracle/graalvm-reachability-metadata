/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.Exchange;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.jupiter.api.Test;

public class XsltBuilderTest {
    @Test
    void transformsWithSaxonTemplatesAndInitializesMessageEmitter() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        XsltBuilder builder = null;
        try {
            context.start();
            builder = XsltBuilder.xslt(createSaxonTemplates());
            builder.setCamelContext(context);
            builder.start();

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("<name>Camel</name>");

            builder.process(exchange);

            assertThat(exchange.getOut().getBody(String.class)).isEqualTo("Hello Camel");
        } finally {
            if (builder != null) {
                builder.stop();
            }
            context.stop();
        }
    }

    private static Templates createSaxonTemplates() throws Exception {
        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        Source source = new StreamSource(new StringReader("""
                <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/">
                    <xsl:text>Hello </xsl:text>
                    <xsl:value-of select="/name"/>
                  </xsl:template>
                </xsl:stylesheet>
                """));
        return factory.newTemplates(source);
    }
}
