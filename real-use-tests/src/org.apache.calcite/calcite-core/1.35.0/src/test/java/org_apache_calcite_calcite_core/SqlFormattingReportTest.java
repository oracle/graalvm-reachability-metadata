/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_calcite_core;

import org.apache.calcite.sql.SqlDialect.DatabaseProduct;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlWriterConfig;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.pretty.SqlPrettyWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Calcite's SQL formatting subsystem by rendering an application report query for an audit log.
public class SqlFormattingReportTest {
    @Test
    @Timeout(60)
    public void formatsCustomerRevenueReportForAuditLog() throws Exception {
        String request = """
                SELECT c.customer_id, SUM(o.amount) AS total_amount
                FROM orders AS o
                JOIN customers AS c ON c.id = o.customer_id
                WHERE o.status = 'PAID'
                GROUP BY c.customer_id
                ORDER BY total_amount DESC
                """;

        SqlNode report = SqlParser.create(request).parseQuery();
        SqlWriterConfig config = SqlPrettyWriter.config()
                .withDialect(DatabaseProduct.POSTGRESQL.getDialect())
                .withKeywordsLowerCase(true)
                .withClauseStartsLine(true)
                .withSelectListItemsOnSeparateLines(true);
        SqlPrettyWriter writer = new SqlPrettyWriter(config);
        String formatted = writer.format(report);

        assertEquals("""
                select "C"."CUSTOMER_ID",
                    sum("O"."AMOUNT") as "TOTAL_AMOUNT"
                from "ORDERS" as "O"
                    inner join "CUSTOMERS" as "C" on "C"."ID" = "O"."CUSTOMER_ID"
                where "O"."STATUS" = 'PAID'
                group by "C"."CUSTOMER_ID"
                order by "TOTAL_AMOUNT" desc""", formatted);
    }
}
