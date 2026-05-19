/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;

import static org.assertj.core.api.Assertions.assertThat;

public class FlywayMigrationInitializerTest {

    @Test
    void usesReflectiveFallbackWhenCompiledMigrateMethodIsUnavailable() throws Exception {
        ThrowOnceFlyway flyway = new ThrowOnceFlyway();
        FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway);

        initializer.afterPropertiesSet();

        assertThat(flyway.migrateInvocations).isEqualTo(2);
    }

    public static class ThrowOnceFlyway extends Flyway {

        private int migrateInvocations;

        ThrowOnceFlyway() {
            super(Flyway.configure());
        }

        @Override
        public MigrateResult migrate() {
            this.migrateInvocations++;
            if (this.migrateInvocations == 1) {
                throw new NoSuchMethodError("migrate");
            }
            return null;
        }

    }

}
