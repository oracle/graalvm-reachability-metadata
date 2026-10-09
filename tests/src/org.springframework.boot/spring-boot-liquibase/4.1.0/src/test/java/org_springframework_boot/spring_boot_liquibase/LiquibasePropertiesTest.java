/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_liquibase;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class LiquibasePropertiesTest {

    @Test
    void propertiesExposeMigrationDefaults() {
        LiquibaseProperties properties = new LiquibaseProperties();

        assertThat(properties.getChangeLog()).isEqualTo("classpath:/db/changelog/db.changelog-master.yaml");
        assertThat(properties.getDatabaseChangeLogTable()).isEqualTo("DATABASECHANGELOG");
        assertThat(properties.getDatabaseChangeLogLockTable()).isEqualTo("DATABASECHANGELOGLOCK");
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isClearChecksums()).isFalse();
        assertThat(properties.isDropFirst()).isFalse();
        assertThat(properties.getContexts()).isNull();
        assertThat(properties.getParameters()).isNull();
    }

    @Test
    void propertiesBindMigrationSettingsFromConfigurationProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "spring.liquibase.change-log", "db/changelog/tenant.sql",
                "spring.liquibase.default-schema", "orders",
                "spring.liquibase.drop-first", "true",
                "spring.liquibase.enabled", "false",
                "spring.liquibase.contexts[0]", "tenant-a",
                "spring.liquibase.contexts[1]", "tenant-b",
                "spring.liquibase.label-filter[0]", "release",
                "spring.liquibase.parameters.tenant", "orders",
                "spring.liquibase.show-summary", "verbose",
                "spring.liquibase.show-summary-output", "console"));

        LiquibaseProperties properties = new Binder(source)
                .bind("spring.liquibase", Bindable.of(LiquibaseProperties.class))
                .orElseThrow(() -> new AssertionError("Liquibase properties were not bound"));

        assertThat(properties.getChangeLog()).isEqualTo("db/changelog/tenant.sql");
        assertThat(properties.getDefaultSchema()).isEqualTo("orders");
        assertThat(properties.isDropFirst()).isTrue();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getContexts()).containsExactly("tenant-a", "tenant-b");
        assertThat(properties.getLabelFilter()).containsExactly("release");
        assertThat(properties.getParameters()).containsEntry("tenant", "orders");
        assertThat(properties.getShowSummary()).isEqualTo(LiquibaseProperties.ShowSummary.VERBOSE);
        assertThat(properties.getShowSummaryOutput()).isEqualTo(LiquibaseProperties.ShowSummaryOutput.CONSOLE);
    }

}
