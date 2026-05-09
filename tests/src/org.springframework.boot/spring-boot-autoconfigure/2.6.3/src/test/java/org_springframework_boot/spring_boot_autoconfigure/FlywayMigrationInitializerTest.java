/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;

import static org.assertj.core.api.Assertions.assertThat;

public class FlywayMigrationInitializerTest {

    @Test
    void invokesMigrateMethodReflectivelyAfterNoSuchMethodError() throws Exception {
        NoSuchMethodFlyway flyway = new NoSuchMethodFlyway();
        FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway);

        initializer.afterPropertiesSet();

        assertThat(flyway.getMigrateCallCount()).isEqualTo(2);
    }

    public static final class NoSuchMethodFlyway extends Flyway {

        private int migrateCallCount;

        public NoSuchMethodFlyway() {
            super(new ClassicConfiguration());
        }

        @Override
        public MigrateResult migrate() {
            this.migrateCallCount++;
            if (this.migrateCallCount == 1) {
                throw new NoSuchMethodError("Simulated legacy Flyway migrate signature");
            }
            return null;
        }

        private int getMigrateCallCount() {
            return this.migrateCallCount;
        }

    }

}
