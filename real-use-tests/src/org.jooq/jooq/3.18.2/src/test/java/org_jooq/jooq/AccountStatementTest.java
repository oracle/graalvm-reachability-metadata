/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's window-function query subsystem to produce account statements with running balances.
 */
public class AccountStatementTest {
    private static final Table<Record> ENTRIES = DSL.table(DSL.name("ledger_entry"));
    private static final Field<Integer> ENTRY_ID = DSL.field(DSL.name("entry_id"), Integer.class);
    private static final Field<String> ACCOUNT = DSL.field(DSL.name("account"), String.class);
    private static final Field<LocalDate> POSTED_ON = DSL.field(DSL.name("posted_on"), LocalDate.class);
    private static final Field<String> DESCRIPTION = DSL.field(DSL.name("description"), String.class);
    private static final Field<BigDecimal> AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);

    @Test
    @Timeout(60)
    public void buildsMonthlyStatementsWithoutLosingOpeningBalances() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:account_statements", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(ENTRIES)
                    .column(ENTRY_ID, SQLDataType.INTEGER.nullable(false))
                    .column(ACCOUNT, SQLDataType.VARCHAR(40).nullable(false))
                    .column(POSTED_ON, SQLDataType.LOCALDATE.nullable(false))
                    .column(DESCRIPTION, SQLDataType.VARCHAR(80).nullable(false))
                    .column(AMOUNT, SQLDataType.DECIMAL(12, 2).nullable(false))
                    .primaryKey(ENTRY_ID)
                    .execute();

            // Insert out of posting order, including two entries on the same day.
            database.insertInto(ENTRIES, ENTRY_ID, ACCOUNT, POSTED_ON, DESCRIPTION, AMOUNT)
                    .values(3, "daily", LocalDate.of(2024, 2, 1), "Refund", new BigDecimal("200.00"))
                    .values(1, "daily", LocalDate.of(2024, 1, 31), "Opening deposit", new BigDecimal("1000.00"))
                    .values(6, "savings", LocalDate.of(2024, 1, 15), "Opening deposit", new BigDecimal("5000.00"))
                    .values(2, "daily", LocalDate.of(2024, 2, 1), "Rent", new BigDecimal("-600.00"))
                    .values(4, "daily", LocalDate.of(2024, 2, 3), "Groceries", new BigDecimal("-75.50"))
                    .values(7, "savings", LocalDate.of(2024, 2, 1), "Transfer in", new BigDecimal("100.00"))
                    .values(5, "daily", LocalDate.of(2024, 3, 1), "Subscription", new BigDecimal("-25.00"))
                    .execute();

            List<StatementLine> statement = monthlyStatement(database,
                    LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1));

            assertEquals(List.of(
                    new StatementLine("daily", 2, LocalDate.of(2024, 2, 1), "Rent",
                            new BigDecimal("-600.00"), new BigDecimal("400.00")),
                    new StatementLine("daily", 3, LocalDate.of(2024, 2, 1), "Refund",
                            new BigDecimal("200.00"), new BigDecimal("600.00")),
                    new StatementLine("daily", 4, LocalDate.of(2024, 2, 3), "Groceries",
                            new BigDecimal("-75.50"), new BigDecimal("524.50")),
                    new StatementLine("savings", 7, LocalDate.of(2024, 2, 1), "Transfer in",
                            new BigDecimal("100.00"), new BigDecimal("5100.00"))), statement);
        }
    }

    private static List<StatementLine> monthlyStatement(DSLContext database,
                                                       LocalDate startInclusive, LocalDate endExclusive) {
        Field<BigDecimal> balance = DSL.sum(AMOUNT).over()
                .partitionBy(ACCOUNT)
                .orderBy(POSTED_ON, ENTRY_ID)
                .rowsUnboundedPreceding()
                .as("balance");

        // Compute over the entire ledger before restricting the displayed statement period.
        Table<?> ledger = database.select(ACCOUNT, ENTRY_ID, POSTED_ON, DESCRIPTION, AMOUNT, balance)
                .from(ENTRIES)
                .asTable("ledger");

        return database.select(ledger.field(ACCOUNT), ledger.field(ENTRY_ID), ledger.field(POSTED_ON),
                        ledger.field(DESCRIPTION), ledger.field(AMOUNT), ledger.field(balance))
                .from(ledger)
                .where(ledger.field(POSTED_ON).ge(startInclusive)
                        .and(ledger.field(POSTED_ON).lt(endExclusive)))
                .orderBy(ledger.field(ACCOUNT), ledger.field(POSTED_ON), ledger.field(ENTRY_ID))
                .fetch(Records.mapping(StatementLine::new));
    }

    private record StatementLine(String account, int entryId, LocalDate postedOn, String description,
                                 BigDecimal amount, BigDecimal balance) {
    }
}
