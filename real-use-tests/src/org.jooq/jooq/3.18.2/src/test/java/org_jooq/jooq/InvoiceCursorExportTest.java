/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record4;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's lazy-cursor subsystem to stream monthly invoices into an accounting export.
 */
public class InvoiceCursorExportTest {
    private static final Table<Record> INVOICES = DSL.table(DSL.name("export_invoice"));
    private static final Field<Integer> NUMBER = DSL.field(DSL.name("invoice_number"), Integer.class);
    private static final Field<LocalDate> ISSUED_ON = DSL.field(DSL.name("issued_on"), LocalDate.class);
    private static final Field<String> CUSTOMER = DSL.field(DSL.name("customer_code"), String.class);
    private static final Field<BigDecimal> TOTAL = DSL.field(DSL.name("total"), BigDecimal.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);

    @Test
    @Timeout(60)
    public void streamsOnlyPostedInvoicesInTheRequestedMonth() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:invoice_cursor_export", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(INVOICES)
                    .column(NUMBER, SQLDataType.INTEGER.nullable(false))
                    .column(ISSUED_ON, SQLDataType.LOCALDATE.nullable(false))
                    .column(CUSTOMER, SQLDataType.VARCHAR(40).nullable(false))
                    .column(TOTAL, SQLDataType.DECIMAL(12, 2).nullable(false))
                    .column(STATUS, SQLDataType.VARCHAR(20).nullable(false))
                    .primaryKey(NUMBER)
                    .execute();
            database.insertInto(INVOICES, NUMBER, ISSUED_ON, CUSTOMER, TOTAL, STATUS)
                    .values(104, LocalDate.of(2024, 2, 15), "C-ADA", new BigDecimal("75.25"), "POSTED")
                    .values(100, LocalDate.of(2024, 1, 31), "C-ADA", new BigDecimal("900.00"), "POSTED")
                    .values(103, LocalDate.of(2024, 2, 10), "C-LINUS", new BigDecimal("32.00"), "POSTED")
                    .values(102, LocalDate.of(2024, 2, 10), "C-GRACE", new BigDecimal("120.50"), "POSTED")
                    .values(101, LocalDate.of(2024, 2, 1), "C-ADA", new BigDecimal("10.00"), "POSTED")
                    .values(105, LocalDate.of(2024, 2, 20), "C-GRACE", new BigDecimal("999.00"), "DRAFT")
                    .values(106, LocalDate.of(2024, 2, 29), "C-LINUS", new BigDecimal("18.00"), "POSTED")
                    .values(107, LocalDate.of(2024, 3, 1), "C-ADA", new BigDecimal("250.00"), "POSTED")
                    .execute();

            StringWriter february = new StringWriter();
            exportMonth(database, LocalDate.of(2024, 2, 1), february);
            assertEquals("""
                    invoice\tissued_on\tcustomer\ttotal
                    101\t2024-02-01\tC-ADA\t10.00
                    102\t2024-02-10\tC-GRACE\t120.50
                    103\t2024-02-10\tC-LINUS\t32.00
                    104\t2024-02-15\tC-ADA\t75.25
                    106\t2024-02-29\tC-LINUS\t18.00
                    """, february.toString());

            // A later export on the same connection sees a fresh result, not the exhausted cursor.
            StringWriter march = new StringWriter();
            exportMonth(database, LocalDate.of(2024, 3, 1), march);
            assertEquals("""
                    invoice\tissued_on\tcustomer\ttotal
                    107\t2024-03-01\tC-ADA\t250.00
                    """, march.toString());

            StringWriter april = new StringWriter();
            exportMonth(database, LocalDate.of(2024, 4, 1), april);
            assertEquals("invoice\tissued_on\tcustomer\ttotal\n", april.toString());
        }
    }

    private static void exportMonth(DSLContext database, LocalDate month, Writer output) throws IOException {
        output.write("invoice\tissued_on\tcustomer\ttotal\n");
        // Consume rows while the JDBC cursor is open rather than materialising the entire export.
        try (Cursor<Record4<Integer, LocalDate, String, BigDecimal>> invoices = database
                .select(NUMBER, ISSUED_ON, CUSTOMER, TOTAL)
                .from(INVOICES)
                .where(STATUS.eq("POSTED"))
                .and(ISSUED_ON.ge(month).and(ISSUED_ON.lt(month.plusMonths(1))))
                .orderBy(ISSUED_ON, NUMBER)
                .fetchSize(2)
                .fetchLazy()) {
            for (Record4<Integer, LocalDate, String, BigDecimal> invoice : invoices) {
                output.write(invoice.value1() + "\t" + invoice.value2() + "\t"
                        + invoice.value3() + "\t" + invoice.value4().toPlainString() + "\n");
            }
        }
    }
}
