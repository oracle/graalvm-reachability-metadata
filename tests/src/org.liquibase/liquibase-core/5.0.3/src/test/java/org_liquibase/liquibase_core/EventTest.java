/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.analytics.Event;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EventTest {

    @Test
    void getPropertiesAsMapIncludesEventProperties() {
        Event event = new Event("update");
        event.setReportsEnabled(true);
        event.setDbclhEnabled(true);
        event.setStructuredLogsEnabled(true);
        event.setOperationOutcome("success");
        event.setExceptionClass("none");
        event.incrementSqlChangelogCount();
        event.incrementFormattedSqlChangelogCount();
        event.incrementXmlChangelogCount();
        event.incrementJsonChangelogCount();
        event.incrementYamlChangelogCount();
        event.setDatabasePlatform("h2");
        event.setDatabaseVersion("2.1");

        Map<String, Object> properties = new LinkedHashMap<>(event.getPropertiesAsMap());

        assertThat(properties)
                .containsEntry("command", "update")
                .containsEntry("reportsEnabled", true)
                .containsEntry("dbclhEnabled", true)
                .containsEntry("structuredLogsEnabled", true)
                .containsEntry("operationOutcome", "success")
                .containsEntry("exceptionClass", "none")
                .containsEntry("chlog_sql", 1)
                .containsEntry("chlog_formattedSql", 1)
                .containsEntry("chlog_xml", 1)
                .containsEntry("chlog_json", 1)
                .containsEntry("chlog_yaml", 1)
                .containsEntry("databasePlatform", "h2")
                .containsEntry("databaseVersion", "2.1")
                .doesNotContainKeys("childEvents", "timestamp");
        assertThat(properties)
                .containsKeys("liquibaseVersion", "liquibaseInterface", "javaVersion", "os", "osVersion", "osArch",
                        "isDocker", "isLiquibaseDocker", "isAwsLiquibaseDocker", "isGithubActions", "isCi", "isIO");
    }
}
