/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvNumber;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's locale-aware bean conversion for importing and exporting an invoice feed.
public class CsvLocalizedInvoiceTest {
    @Test
    @Timeout(120)
    public void convertsLocalizedInvoiceFieldsForBillingWorkflow() throws Exception {
        String incoming = "invoice_id;customer;issued_on;net_amount\n"
                + "INV-17;Alpine Supplies;31.01.2024;1.234,50\n";

        List<InvoiceLine> invoices = new CsvToBeanBuilder<InvoiceLine>(new StringReader(incoming))
                .withType(InvoiceLine.class)
                .withSeparator(';')
                .build()
                .parse();

        assertEquals(1, invoices.size());
        InvoiceLine invoice = invoices.get(0);
        assertEquals("INV-17", invoice.getInvoiceId());
        assertEquals("Alpine Supplies", invoice.getCustomer());
        assertEquals(LocalDate.of(2024, 1, 31), invoice.getIssuedOn());
        assertEquals(new BigDecimal("1234.50"), invoice.getNetAmount());

        StringWriter exported = new StringWriter();
        StatefulBeanToCsv<InvoiceLine> writer = new StatefulBeanToCsvBuilder<InvoiceLine>(exported)
                .withSeparator(';')
                .withLineEnd("\n")
                .build();
        writer.write(invoices);

        assertEquals("\"CUSTOMER\";\"INVOICE_ID\";\"ISSUED_ON\";\"NET_AMOUNT\"\n"
                + "\"Alpine Supplies\";\"INV-17\";\"31.01.2024\";\"1.234,50\"\n", exported.toString());
    }

    public static class InvoiceLine {
        @CsvBindByName(column = "invoice_id", required = true)
        private String invoiceId;

        @CsvBindByName(column = "customer", required = true)
        private String customer;

        @CsvDate("dd.MM.yyyy")
        @CsvBindByName(column = "issued_on", required = true, locale = "de-DE")
        private LocalDate issuedOn;

        @CsvNumber("#.##0,00")
        @CsvBindByName(column = "net_amount", required = true, locale = "de-DE")
        private BigDecimal netAmount;

        public InvoiceLine() {
        }

        public String getInvoiceId() {
            return invoiceId;
        }

        public String getCustomer() {
            return customer;
        }

        public LocalDate getIssuedOn() {
            return issuedOn;
        }

        public BigDecimal getNetAmount() {
            return netAmount;
        }
    }
}
