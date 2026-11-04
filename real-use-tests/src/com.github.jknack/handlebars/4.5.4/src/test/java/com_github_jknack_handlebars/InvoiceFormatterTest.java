/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Formatter;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' formatter subsystem to render an invoice's application money value.
public class InvoiceFormatterTest {
    @Test
    @Timeout(60)
    public void formatsDomainMoneyThroughTheTemplatePipeline() throws Exception {
        Handlebars handlebars = new Handlebars().with(new MoneyFormatter());
        Template template = handlebars.compileInline(
                "<article><h1>{{number}}</h1>"
                        + "<p class=\"total\">{{total}}</p>"
                        + "<p class=\"customer\">{{customer}}</p></article>");

        String rendered = template.apply(Map.of(
                "number", "INV-1042",
                "total", new Money("USD", new BigDecimal("42.5")),
                "customer", "Mara & Co."));

        assertEquals(
                "<article><h1>INV-1042</h1><p class=\"total\">USD 42.50</p>"
                        + "<p class=\"customer\">Mara &amp; Co.</p></article>",
                rendered);
    }

    private static final class MoneyFormatter implements Formatter {
        @Override
        public Object format(Object value, Chain next) {
            if (value instanceof Money money) {
                return money.currency() + " " + money.amount().setScale(2).toPlainString();
            }
            return next.format(value);
        }
    }

    private record Money(String currency, BigDecimal amount) {
    }
}
