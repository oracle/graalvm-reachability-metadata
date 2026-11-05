/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb_hsqldb;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HSQLDB's JDBC stored-procedure subsystem for calculating an order total.
public class StoredProcedureOrderTotalTest {
    @Test
    @Timeout(60)
    public void callsAnOrderTotalProcedureThroughJdbc() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:stored_procedure_order_total_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE PROCEDURE order_total(IN item_price DECIMAL(10, 2),
                                                 IN item_count INTEGER,
                                                 OUT total DECIMAL(10, 2))
                    BEGIN ATOMIC
                        SET total = item_price * item_count;
                    END
                    """);
        }

        try (Connection connection = dataSource.getConnection();
                CallableStatement procedure = connection.prepareCall("{call order_total(?, ?, ?)}")) {
            procedure.setBigDecimal(1, new BigDecimal("19.95"));
            procedure.setInt(2, 3);
            procedure.registerOutParameter(3, Types.DECIMAL);

            assertEquals(false, procedure.execute());
            assertEquals(new BigDecimal("59.85"), procedure.getBigDecimal(3));
        }
    }
}
