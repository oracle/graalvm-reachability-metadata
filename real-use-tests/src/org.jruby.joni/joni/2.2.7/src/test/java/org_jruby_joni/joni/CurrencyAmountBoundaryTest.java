/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_joni.joni;

import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joni's lookaround matching subsystem to extract standalone USD amounts from invoice text.
public class CurrencyAmountBoundaryTest {
    @Test
    @Timeout(60)
    public void extractsAmountsWithoutMatchingEmbeddedProductCodes() {
        Regex amount = new Regex("(?<=USD )\\d+(?![\\dA-Z])");
        byte[] invoice = "Invoice EUR 100 USD 123 USD 456X USD 789."
                .getBytes(StandardCharsets.UTF_8);
        Matcher matcher = amount.matcher(invoice);

        int firstStart = invoiceText(invoice).indexOf("USD 123") + "USD ".length();
        int secondStart = invoiceText(invoice).indexOf("USD 789") + "USD ".length();

        assertEquals(firstStart, matcher.search(0, invoice.length, Option.DEFAULT));
        assertEquals("123", matchedText(invoice, matcher));
        assertEquals(secondStart, matcher.search(matcher.getEnd(), invoice.length, Option.DEFAULT));
        assertEquals("789", matchedText(invoice, matcher));
        assertEquals(Matcher.FAILED, matcher.search(matcher.getEnd(), invoice.length, Option.DEFAULT));
    }

    private static String invoiceText(byte[] invoice) {
        return new String(invoice, StandardCharsets.UTF_8);
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
