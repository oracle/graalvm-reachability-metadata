/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's XSLT compilation and serialization subsystem to render a warehouse inventory
 * report from an XML feed.
 *
 * An operations service compiles its report stylesheet once and applies it to the inventory feed
 * for each warehouse. The report includes only items with stock remaining, preserving the feed's
 * order so the generated text can be sent directly to a dashboard.
 */
public class WarehouseInventoryReportTest {

    private static final String INVENTORY = """
            <inventory xmlns="urn:inventory">
              <item sku="NOTEBOOK-42" warehouse="north" quantity="18"/>
              <item sku="PENS-7" warehouse="north" quantity="4"/>
              <item sku="MUG-3" warehouse="north" quantity="0"/>
              <item sku="CABLE-9" warehouse="south" quantity="12"/>
            </inventory>
            """;

    private static final String REPORT_STYLESHEET = """
            <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:inv="urn:inventory"
                    exclude-result-prefixes="inv">
              <xsl:output method="text"/>
              <xsl:param name="warehouse"/>
              <xsl:template match="/">
                <xsl:for-each select="/inv:inventory/inv:item[
                        @warehouse = $warehouse and number(@quantity) &gt; 0]">
                  <xsl:if test="position() &gt; 1"><xsl:text>,</xsl:text></xsl:if>
                  <xsl:value-of select="@sku"/>
                  <xsl:text>:</xsl:text>
                  <xsl:value-of select="@quantity"/>
                </xsl:for-each>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    @Timeout(60)
    public void rendersStockedItemsForEachWarehouse() throws Exception {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable report = compiler.compile(
                new StreamSource(new StringReader(REPORT_STYLESHEET)));

        assertEquals("NOTEBOOK-42:18,PENS-7:4", render(report, processor, "north"));
        assertEquals("CABLE-9:12", render(report, processor, "south"));
        assertEquals("", render(report, processor, "west"));
    }

    private static String render(XsltExecutable report, Processor processor, String warehouse)
            throws Exception {
        XsltTransformer transformer = report.load();
        transformer.setSource(new StreamSource(new StringReader(INVENTORY)));
        transformer.setParameter(new QName("warehouse"), new XdmAtomicValue(warehouse));
        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.setDestination(serializer);
        transformer.transform();
        return output.toString();
    }
}
