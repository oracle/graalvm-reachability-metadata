/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's SQL script subsystem for provisioning notification preferences.
public class NotificationSchemaScriptTest {
    @Test
    void appliesSchemaAndSeedDataAsOneApplicationScript() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:notification_preferences");

        jdbi.useHandle(handle -> {
            handle.createScript("""
                    CREATE TABLE notification_preference (
                        user_id VARCHAR(32) NOT NULL,
                        channel VARCHAR(16) NOT NULL,
                        enabled BOOLEAN NOT NULL,
                        PRIMARY KEY (user_id, channel)
                    );
                    INSERT INTO notification_preference (user_id, channel, enabled)
                    VALUES ('user-1042', 'email', TRUE);
                    INSERT INTO notification_preference (user_id, channel, enabled)
                    VALUES ('user-1042', 'sms', FALSE);
                    """).executeAsSeparateStatements();

            assertEquals(List.of(
                    Map.of("channel", "email", "enabled", true),
                    Map.of("channel", "sms", "enabled", false)),
                    handle.createQuery("""
                            SELECT channel, enabled
                            FROM notification_preference
                            WHERE user_id = :userId
                            ORDER BY channel
                            """)
                            .bind("userId", "user-1042")
                            .mapToMap()
                            .list());
        });
    }
}
