/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.HTMLWriter;
import org.dom4j.io.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises dom4j's HTML writer subsystem to render an invoice for a web application.
public class HtmlInvoiceRendererTest {
    @Test
    @Timeout(60)
    public void rendersInvoiceAsHtml() throws Exception {
        Document invoice = DocumentHelper.createDocument();
        Element html = invoice.addElement("html");
        html.addElement("head").addElement("title").addText("Invoice INV-42");
        Element body = html.addElement("body");
        body.addElement("h1").addText("Invoice INV-42");
        Element table = body.addElement("table").addAttribute("class", "invoice-lines");
        Element header = table.addElement("tr");
        header.addElement("th").addText("Item");
        header.addElement("th").addText("Amount");
        Element line = table.addElement("tr");
        line.addElement("td").addText("Bread & Butter");
        line.addElement("td").addText("EUR 12.50");
        body.addElement("p").addText("Thank you for your business.");

        StringWriter output = new StringWriter();
        HTMLWriter writer = new HTMLWriter(output, OutputFormat.createPrettyPrint());
        writer.write(invoice);
        writer.close();

        String rendered = output.toString();
        assertTrue(rendered.contains("<title>Invoice INV-42</title>"));
        assertTrue(rendered.contains("<table class=\"invoice-lines\">"));
        assertTrue(rendered.contains("Bread &amp; Butter"));
        assertTrue(rendered.contains("EUR 12.50"));
        assertTrue(rendered.contains("Thank you for your business."));
        assertFalse(rendered.contains("<html/>"));
    }
}
