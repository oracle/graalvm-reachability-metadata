/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.Configuration;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Hibernate JSON basic-type mapping to store and edit account notification preferences.
@Timeout(60)
public class NotificationPreferencesJsonTest {

    @Test
    public void persistsPreferenceDocumentsAndTracksEditsWithoutChangingOtherAccounts() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Account.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:notification_preferences_json;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("hibernate.type.json_format_mapper", "jackson")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Account(1L, "Ada", Map.of(
                        "channel", "email", "digest", "daily", "label", "Orders \"priority\"")));
                session.persist(new Account(2L, "Ben", Map.of("channel", "sms", "digest", "weekly")));
                session.persist(new Account(3L, "Cora", null));
            });

            factory.inTransaction(session -> {
                Account account = session.find(Account.class, 1L);
                assertNotNull(account);
                assertEquals("Ada", account.name);
                assertEquals(Map.of("channel", "email", "digest", "daily", "label", "Orders \"priority\""),
                        account.preferences);

                // Editing the managed document should be persisted by Hibernate's dirty checking.
                account.preferences.put("digest", "weekly");
                account.preferences.put("timezone", "Europe/Paris");
                account.preferences.remove("label");

                Account withoutPreferences = session.find(Account.class, 3L);
                assertNotNull(withoutPreferences);
                assertNull(withoutPreferences.preferences);
                withoutPreferences.preferences = new LinkedHashMap<>(Map.of("channel", "push"));
            });

            factory.inTransaction(session -> {
                Account changed = session.find(Account.class, 1L);
                assertNotNull(changed);
                assertEquals(Map.of("channel", "email", "digest", "weekly", "timezone", "Europe/Paris"),
                        changed.preferences);

                Account unchanged = session.find(Account.class, 2L);
                assertNotNull(unchanged);
                assertEquals("Ben", unchanged.name);
                assertEquals(Map.of("channel", "sms", "digest", "weekly"), unchanged.preferences);

                Account initialized = session.find(Account.class, 3L);
                assertNotNull(initialized);
                assertEquals(Map.of("channel", "push"), initialized.preferences);
                changed.preferences = null;
            });

            factory.inTransaction(session -> {
                Account cleared = session.find(Account.class, 1L);
                assertNotNull(cleared);
                assertEquals("Ada", cleared.name);
                assertNull(cleared.preferences);
                assertEquals(3L, session.createQuery(
                        "select count(a) from NotificationAccount a", Long.class).getSingleResult());
            });
        }
    }

    @Entity(name = "NotificationAccount")
    @Table(name = "notification_account")
    public static final class Account {
        @Id
        private Long id;

        private String name;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "notification_preferences")
        private Map<String, String> preferences;

        public Account() {
        }

        public Account(Long id, String name, Map<String, String> preferences) {
            this.id = id;
            this.name = name;
            this.preferences = preferences == null ? null : new LinkedHashMap<>(preferences);
        }
    }
}
