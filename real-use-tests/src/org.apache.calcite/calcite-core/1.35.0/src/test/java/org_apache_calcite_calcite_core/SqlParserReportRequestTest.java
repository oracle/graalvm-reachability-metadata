/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_calcite_core;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Calcite's SQL parser subsystem by inspecting an application report request before execution.
public class SqlParserReportRequestTest {
    @Test
    @Timeout(60)
    public void recognizesCustomerRevenueReportRequest() throws Exception {
        String request = """
                SELECT customer_id, SUM(amount) AS total_amount
                FROM orders
                WHERE status = 'PAID'
                GROUP BY customer_id
                ORDER BY customer_id
                """;

        SqlOrderBy orderedReport = (SqlOrderBy) SqlParser.create(request).parseQuery();
        SqlSelect report = (SqlSelect) orderedReport.query;

        assertEquals(SqlKind.ORDER_BY, orderedReport.getKind());
        assertEquals(SqlKind.SELECT, report.getKind());
        assertEquals(
                List.of("CUSTOMER_ID", "SUM(`AMOUNT`) AS `TOTAL_AMOUNT`"),
                report.getSelectList().getList().stream().map(SqlNode::toString).toList());
        assertEquals("ORDERS", ((SqlIdentifier) report.getFrom()).getSimple().toUpperCase());
        assertEquals(SqlKind.EQUALS, report.getWhere().getKind());
        assertEquals(List.of("CUSTOMER_ID"), report.getGroup().getList().stream()
                .map(SqlNode::toString)
                .toList());
        assertEquals(List.of("CUSTOMER_ID"), orderedReport.orderList.getList().stream()
                .map(SqlNode::toString)
                .toList());

        SqlCall aggregateSelection = (SqlCall) report.getSelectList().get(1);
        assertEquals(SqlKind.AS, aggregateSelection.getKind());
        assertEquals("TOTAL_AMOUNT", ((SqlIdentifier) aggregateSelection.operand(1))
                .getSimple().toUpperCase());
        assertEquals("SUM", ((SqlCall) aggregateSelection.operand(0)).getOperator().getName());
        assertTrue(report.hasWhere());
        assertTrue(orderedReport.orderList.size() > 0);
    }
}
