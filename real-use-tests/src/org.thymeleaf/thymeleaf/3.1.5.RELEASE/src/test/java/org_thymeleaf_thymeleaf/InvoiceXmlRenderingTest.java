/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf_thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's XML template processing subsystem for an invoice export.
public class InvoiceXmlRenderingTest {
    @Test
    public void rendersEscapedInvoiceDocumentWithRepeatedLineItems() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("invoice", new Invoice(
                "INV-1042",
                "Mina Patel",
                List.of(
                        new LineItem("USB & adapter", 2),
                        new LineItem("Notebook", 1)),
                new BigDecimal("39.98")));

        String rendered = templateEngine.process(INVOICE_TEMPLATE, context);
        String normalized = rendered.replaceAll("\\s+", " ").trim();

        assertTrue(normalized.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(normalized.contains("<number>INV-1042</number>"));
        assertTrue(normalized.contains("<customer>Mina Patel</customer>"));
        assertTrue(normalized.contains("<description>USB &amp; adapter</description>"));
        assertTrue(normalized.contains("<description>Notebook</description>"));
        assertTrue(normalized.contains("<quantity>2</quantity>"));
        assertTrue(normalized.contains("<quantity>1</quantity>"));
        assertTrue(normalized.contains("<total>39.98</total>"));
        assertEquals(2, countOccurrences(normalized, "<line>"));
        assertFalse(normalized.contains("th:"));
    }

    private static int countOccurrences(String value, String searched) {
        int count = 0;
        int start = 0;
        while ((start = value.indexOf(searched, start)) >= 0) {
            count++;
            start += searched.length();
        }
        return count;
    }

    private record Invoice(String number, String customer, List<LineItem> lines, BigDecimal total) {
    }

    private record LineItem(String description, int quantity) {
    }

    private static final String INVOICE_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <invoice xmlns:th="http://www.thymeleaf.org">
              <number th:text="${invoice.number}">INV-0</number>
              <customer th:text="${invoice.customer}">Customer</customer>
              <lines>
                <line th:each="line : ${invoice.lines}">
                  <description th:text="${line.description}">Item</description>
                  <quantity th:text="${line.quantity}">0</quantity>
                </line>
              </lines>
              <total th:text="${invoice.total}">0.00</total>
            </invoice>
            """;
}
