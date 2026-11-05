/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GooGooStatementCacheAnonymous1StmtAcquireTaskTest {
    @Test
    void preparesAndReusesCachedStatementsWithFreshParameters() throws Exception {
        ComboPooledDataSource source = C3p0Support.pool();
        try {
            source.setMaxStatementsPerConnection(3);
            try (Connection connection = source.getConnection()) {
                for (int expected = 63; expected <= 64; expected++) {
                    try (PreparedStatement statement = connection.prepareStatement("SELECT ?")) {
                        statement.setInt(1, expected);
                        try (ResultSet rows = statement.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals(expected, rows.getInt(1));
                        }
                    }
                }
                assertEquals(1, source.getStatementCacheNumStatementsDefaultUser());
                assertEquals(0, source.getStatementCacheNumCheckedOutDefaultUser());
            }
        } finally {
            source.close();
        }
    }
}
