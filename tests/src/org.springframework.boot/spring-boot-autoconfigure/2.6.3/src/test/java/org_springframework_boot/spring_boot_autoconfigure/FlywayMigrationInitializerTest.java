/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class FlywayMigrationInitializerTest {

    @Test
    void initializationMigratesSchemaAndDoesNotRepeatAppliedMigration() throws Exception {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:initializer_default;DB_CLOSE_DELAY=0", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            Flyway flyway = Flyway.configure().dataSource(dataSource)
                    .locations("classpath:db/initializer-migration").load();
            FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway);
            initializer.setOrder(15);
            assertThat(initializer.getOrder()).isEqualTo(15);

            initializer.afterPropertiesSet();
            initializer.afterPropertiesSet();

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT name FROM migration_items ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("migrated orchard");
                assertThat(rows.next()).isFalse();
            }
            assertThat(flyway.info().applied()).hasSize(1);
            assertThat(flyway.info().pending()).isEmpty();
        }
    }

    @Test
    void customStrategyReceivesAndMigratesConfiguredFlyway() throws Exception {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:initializer_strategy;DB_CLOSE_DELAY=0", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            Flyway flyway = Flyway.configure().dataSource(dataSource)
                    .locations("classpath:db/initializer-migration").load();
            AtomicBoolean invoked = new AtomicBoolean();
            FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway, configured -> {
                assertThat(configured).isSameAs(flyway);
                invoked.set(true);
                configured.migrate();
            });

            initializer.afterPropertiesSet();

            assertThat(invoked).isTrue();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM migration_items")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
