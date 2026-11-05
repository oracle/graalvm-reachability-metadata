/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.TransformerFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's JAXP transformer-factory subsystem to render a customer notification.
 *
 * A notification service uses the standard JAXP interfaces shared by its other XML providers,
 * while selecting Saxon for an XSLT 2.0 stylesheet. It compiles the template once and applies it
 * to each customer document before sending the resulting text to a message queue.
 */
public class JaxpTransformerFactoryTest {

    private static final String CUSTOMER = """
            <customer id="C-17">
              <name>Acme Supplies</name>
              <orders>
                <order number="A-2048" status="ready"/>
                <order number="A-2049" status="backorder"/>
              </orders>
            </customer>
            """;

    private static final String NOTIFICATION_STYLESHEET = """
            <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="text"/>
              <xsl:param name="greeting"/>
              <xsl:template match="/">
                <xsl:value-of select="$greeting"/>
                <xsl:text>, </xsl:text>
                <xsl:value-of select="customer/name"/>
                <xsl:text> (</xsl:text>
                <xsl:value-of select="customer/@id"/>
                <xsl:text>): </xsl:text>
                <xsl:value-of select="string-join(customer/orders/order[@status = 'ready']/@number, ',')"/>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    @Timeout(60)
    public void transformsCustomerUsingJaxpInterfaces() throws Exception {
        TransformerFactory factory = new TransformerFactoryImpl();
        Templates template = factory.newTemplates(
                new StreamSource(new StringReader(NOTIFICATION_STYLESHEET)));
        Transformer transformer = template.newTransformer();
        transformer.setParameter("greeting", "Dispatch update");

        StringWriter output = new StringWriter();
        transformer.transform(
                new StreamSource(new StringReader(CUSTOMER)), new StreamResult(output));

        assertEquals("Dispatch update, Acme Supplies (C-17): A-2048", output.toString());
    }
}
