/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.number.NumberStyleFormatter;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Spring formatting conversion to import localized invoice amounts and export them for another locale.
public class LocalizedInvoiceFormattingTest {

    @Test
    @Timeout(60)
    public void importsLocalizedAmountsWithoutChangingInvoiceDatesOrValues() {
        InvoiceCsvCodec codec = new InvoiceCsvCodec();

        Invoice americanInvoice = codec.read("INV-42;2024-02-29;1,234.50", Locale.US);
        Invoice germanInvoice = codec.read("INV-43;2024-03-01;987,65", Locale.GERMANY);

        assertEquals(new Invoice("INV-42", LocalDate.of(2024, 2, 29), new BigDecimal("1234.50")),
                americanInvoice);
        assertEquals(new Invoice("INV-43", LocalDate.of(2024, 3, 1), new BigDecimal("987.65")),
                germanInvoice);
        assertEquals(new BigDecimal("2222.15"), americanInvoice.amount().add(germanInvoice.amount()));

        String germanExport = codec.write(americanInvoice, Locale.GERMANY);
        String americanExport = codec.write(germanInvoice, Locale.US);
        assertEquals("INV-42;2024-02-29;1.234,50", germanExport);
        assertEquals("INV-43;2024-03-01;987.65", americanExport);
        assertEquals(americanInvoice, codec.read(germanExport, Locale.GERMANY));
        assertEquals(germanInvoice, codec.read(americanExport, Locale.US));

        // Reject unconsumed amount text rather than silently accepting a partial monetary value.
        assertThrows(ConversionFailedException.class,
                () -> codec.read("INV-44;2024-03-02;12.50 USD", Locale.US));
        assertEquals("INV-42;2024-02-29;1,234.50", codec.write(americanInvoice, Locale.US));
    }

    public record Invoice(String number, LocalDate date, BigDecimal amount) {
    }

    public static final class InvoiceCsvCodec {
        private final DefaultFormattingConversionService formats = new DefaultFormattingConversionService(false);

        public InvoiceCsvCodec() {
            DateTimeFormatterRegistrar dates = new DateTimeFormatterRegistrar();
            dates.setUseIsoFormat(true);
            dates.registerFormatters(formats);
            formats.addFormatterForFieldType(BigDecimal.class, new NumberStyleFormatter("#,##0.00"));
        }

        public Invoice read(String row, Locale locale) {
            return withLocale(locale, () -> {
                String[] fields = row.split(";", -1);
                if (fields.length != 3) {
                    throw new IllegalArgumentException("An invoice row must contain number, date and amount");
                }
                return new Invoice(fields[0], formats.convert(fields[1], LocalDate.class),
                        formats.convert(fields[2], BigDecimal.class));
            });
        }

        public String write(Invoice invoice, Locale locale) {
            return withLocale(locale, () -> String.join(";", invoice.number(),
                    formats.convert(invoice.date(), String.class),
                    formats.convert(invoice.amount(), String.class)));
        }

        private static <T> T withLocale(Locale locale, Supplier<T> operation) {
            LocaleContext previous = LocaleContextHolder.getLocaleContext();
            try {
                LocaleContextHolder.setLocale(locale);
                return operation.get();
            } finally {
                LocaleContextHolder.setLocaleContext(previous);
            }
        }
    }
}
