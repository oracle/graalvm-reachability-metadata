/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jdbi's SQL logging subsystem for collecting application query audit events.
public class SqlAuditLoggerTest {
    @Test
    void recordsApplicationStatementsForOperationalAudit() {
        List<SqlEvent> events = new ArrayList<>();
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:sql_audit")
                .setSqlLogger(new SqlLogger() {
                    @Override
                    public void logBeforeExecution(StatementContext context) {
                        events.add(new SqlEvent("before", context.getRawSql()));
                    }

                    @Override
                    public void logAfterExecution(StatementContext context) {
                        events.add(new SqlEvent("after", context.getRawSql()));
                    }
                });

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE audit_note (
                        note_id VARCHAR(32) PRIMARY KEY,
                        message VARCHAR(128) NOT NULL
                    )
                    """);
            handle.createUpdate("""
                    INSERT INTO audit_note (note_id, message)
                    VALUES (:noteId, :message)
                    """)
                    .bind("noteId", "NOTE-1042")
                    .bind("message", "Shipment ready")
                    .execute();

            assertEquals(List.of("Shipment ready"), handle.createQuery("""
                    SELECT message
                    FROM audit_note
                    WHERE note_id = :noteId
                    """)
                    .bind("noteId", "NOTE-1042")
                    .mapTo(String.class)
                    .list());
        });

        List<SqlEvent> businessEvents = events.stream()
                .filter(event -> event.normalizedSql().contains("INSERT INTO AUDIT_NOTE")
                        || event.normalizedSql().contains("SELECT MESSAGE"))
                .toList();
        assertEquals(List.of("before", "after", "before", "after"),
                businessEvents.stream().map(SqlEvent::phase).toList());
        assertTrue(businessEvents.get(0).normalizedSql().contains("INSERT INTO AUDIT_NOTE"));
        assertTrue(businessEvents.get(2).normalizedSql().contains("SELECT MESSAGE"));
    }

    private record SqlEvent(String phase, String sql) {
        private String normalizedSql() {
            return sql.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        }
    }
}
