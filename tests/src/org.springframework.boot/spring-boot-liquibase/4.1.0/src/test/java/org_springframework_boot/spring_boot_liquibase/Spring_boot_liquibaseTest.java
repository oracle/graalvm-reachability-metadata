/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import liquibase.changelog.ChangeSet.ExecType;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.liquibase.actuate.endpoint.LiquibaseEndpoint;
import org.springframework.boot.liquibase.actuate.endpoint.LiquibaseEndpoint.ChangeSetDescriptor;
import org.springframework.boot.liquibase.actuate.endpoint.LiquibaseEndpoint.ContextLiquibaseBeansDescriptor;
import org.springframework.boot.liquibase.actuate.endpoint.LiquibaseEndpoint.LiquibaseBeanDescriptor;
import org.springframework.boot.liquibase.autoconfigure.DataSourceClosingSpringLiquibase;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseConnectionDetails;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

public class Spring_boot_liquibaseTest {

    @TempDir
    Path tempDir;

    @Test
    void autoConfiguredSpringLiquibaseRunsChangelogAgainstApplicationDataSource() throws Exception {
        Path changelog = writeChangelog("application-data-source.sql", """
                --liquibase formatted sql

                --changeset spring-boot-test:create-widget
                CREATE TABLE APP_WIDGET (ID INT PRIMARY KEY, NAME VARCHAR(50) NOT NULL);

                --changeset spring-boot-test:insert-widget
                INSERT INTO APP_WIDGET (ID, NAME) VALUES (1, 'configured by liquibase');
                """);
        String url = "jdbc:h2:mem:auto-configured-liquibase;DB_CLOSE_DELAY=-1";

        try (ConfigurableApplicationContext context = runApplication(DataSourceApplication.class,
                "test.datasource.url=" + url, "spring.liquibase.change-log=" + changelog.toUri())) {
            SpringLiquibase liquibase = context.getBean(SpringLiquibase.class);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(DataSource.class));

            assertThat(liquibase.getChangeLog()).isEqualTo(changelog.toUri().toString());
            assertThat(jdbcTemplate.queryForObject("SELECT NAME FROM APP_WIDGET WHERE ID = 1", String.class))
                    .isEqualTo("configured by liquibase");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN ('create-widget', 'insert-widget')",
                    Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void liquibasePropertiesBindAndControlContextsLabelsAndParameters() throws Exception {
        Path changelog = writeChangelog("filtered-changes.sql", """
                --liquibase formatted sql

                --changeset spring-boot-test:create-filtered contextFilter:ci labels:enabled
                CREATE TABLE ${tablename} (ID INT PRIMARY KEY, DESCRIPTION VARCHAR(100));

                --changeset spring-boot-test:insert-filtered contextFilter:ci labels:enabled
                INSERT INTO ${tablename} (ID, DESCRIPTION) VALUES (100, 'context and label matched');

                --changeset spring-boot-test:skipped-context contextFilter:manual labels:enabled
                CREATE TABLE SKIPPED_CONTEXT_ITEM (ID INT PRIMARY KEY);

                --changeset spring-boot-test:skipped-label contextFilter:ci labels:disabled
                CREATE TABLE SKIPPED_LABEL_ITEM (ID INT PRIMARY KEY);
                """);
        String url = "jdbc:h2:mem:filtered-liquibase;DB_CLOSE_DELAY=-1";

        try (ConfigurableApplicationContext context = runApplication(DataSourceApplication.class,
                "test.datasource.url=" + url,
                "spring.liquibase.change-log=" + changelog.toUri(),
                "spring.liquibase.contexts=ci",
                "spring.liquibase.label-filter=enabled",
                "spring.liquibase.parameters.tablename=FILTERED_ITEM")) {
            LiquibaseProperties properties = context.getBean(LiquibaseProperties.class);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(DataSource.class));

            assertThat(properties.getContexts()).containsExactly("ci");
            assertThat(properties.getLabelFilter()).containsExactly("enabled");
            assertThat(properties.getParameters()).containsEntry("tablename", "FILTERED_ITEM");
            assertThat(tableExists(jdbcTemplate, "FILTERED_ITEM")).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT DESCRIPTION FROM FILTERED_ITEM WHERE ID = 100", String.class))
                    .isEqualTo("context and label matched");
            assertThat(tableExists(jdbcTemplate, "SKIPPED_CONTEXT_ITEM")).isFalse();
            assertThat(tableExists(jdbcTemplate, "SKIPPED_LABEL_ITEM")).isFalse();
        }
    }

    @Test
    void customChangeLogAndLockTableNamesAreUsedForLiquibaseMetadata() throws Exception {
        Path changelog = writeChangelog("custom-tracking-tables.sql", """
                --liquibase formatted sql

                --changeset spring-boot-test:create-custom-tracking-item
                CREATE TABLE CUSTOM_TRACKING_ITEM (ID INT PRIMARY KEY, DESCRIPTION VARCHAR(100));

                --changeset spring-boot-test:insert-custom-tracking-item
                INSERT INTO CUSTOM_TRACKING_ITEM (ID, DESCRIPTION) VALUES (5, 'tracked in custom tables');
                """);
        String url = "jdbc:h2:mem:custom-tracking-liquibase;DB_CLOSE_DELAY=-1";

        try (ConfigurableApplicationContext context = runApplication(DataSourceApplication.class,
                "test.datasource.url=" + url,
                "spring.liquibase.change-log=" + changelog.toUri(),
                "spring.liquibase.database-change-log-table=BOOT_CHANGELOG",
                "spring.liquibase.database-change-log-lock-table=BOOT_CHANGELOG_LOCK")) {
            LiquibaseProperties properties = context.getBean(LiquibaseProperties.class);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(DataSource.class));

            assertThat(properties.getDatabaseChangeLogTable()).isEqualTo("BOOT_CHANGELOG");
            assertThat(properties.getDatabaseChangeLogLockTable()).isEqualTo("BOOT_CHANGELOG_LOCK");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT DESCRIPTION FROM CUSTOM_TRACKING_ITEM WHERE ID = 5", String.class))
                    .isEqualTo("tracked in custom tables");
            assertThat(tableExists(jdbcTemplate, "BOOT_CHANGELOG")).isTrue();
            assertThat(tableExists(jdbcTemplate, "BOOT_CHANGELOG_LOCK")).isTrue();
            assertThat(tableExists(jdbcTemplate, "DATABASECHANGELOG")).isFalse();
            assertThat(tableExists(jdbcTemplate, "DATABASECHANGELOGLOCK")).isFalse();
        }
    }

    @Test
    void liquibaseConnectionDetailsCanSupplyDedicatedMigrationConnection() throws Exception {
        Path changelog = writeChangelog("connection-details.sql", """
                --liquibase formatted sql

                --changeset spring-boot-test:create-from-connection-details
                CREATE TABLE CONNECTION_DETAIL_ITEM (ID INT PRIMARY KEY, DESCRIPTION VARCHAR(100));

                --changeset spring-boot-test:insert-from-connection-details
                INSERT INTO CONNECTION_DETAIL_ITEM (ID, DESCRIPTION) VALUES (10, 'dedicated liquibase connection');
                """);
        String applicationUrl = "jdbc:h2:mem:connection-details-application;DB_CLOSE_DELAY=-1";
        String migrationUrl = "jdbc:h2:mem:connection-details-migration;DB_CLOSE_DELAY=-1";

        try (ConfigurableApplicationContext context = runApplication(ConnectionDetailsApplication.class,
                "test.datasource.url=" + applicationUrl,
                "test.liquibase.url=" + migrationUrl,
                "spring.liquibase.change-log=" + changelog.toUri())) {
            SpringLiquibase liquibase = context.getBean(SpringLiquibase.class);
            JdbcTemplate applicationJdbcTemplate = new JdbcTemplate(context.getBean(DataSource.class));

            assertThat(liquibase).isInstanceOf(DataSourceClosingSpringLiquibase.class);
            assertThat(tableExists(applicationJdbcTemplate, "CONNECTION_DETAIL_ITEM")).isFalse();
            assertConnectionDetailsChangelogWasApplied(migrationUrl);
        }
    }

    @Test
    void liquibaseEndpointReportsExecutedChangeSetsForApplicationContext() throws Exception {
        Path changelog = writeChangelog("endpoint-report.sql", """
                --liquibase formatted sql

                --changeset endpoint-test:create-endpoint-item
                CREATE TABLE ENDPOINT_ITEM (ID INT PRIMARY KEY, DESCRIPTION VARCHAR(100));

                --changeset endpoint-test:insert-endpoint-item
                INSERT INTO ENDPOINT_ITEM (ID, DESCRIPTION) VALUES (20, 'reported by endpoint');
                """);
        String url = "jdbc:h2:mem:endpoint-report-liquibase;DB_CLOSE_DELAY=-1";

        try (ConfigurableApplicationContext context = runApplication(DataSourceApplication.class,
                "test.datasource.url=" + url, "spring.liquibase.change-log=" + changelog.toUri())) {
            LiquibaseEndpoint endpoint = new LiquibaseEndpoint(context);
            Map<String, ContextLiquibaseBeansDescriptor> contexts = endpoint.liquibaseBeans().getContexts();
            ContextLiquibaseBeansDescriptor contextDescriptor = contexts.get(context.getId());
            String[] liquibaseBeanNames = context.getBeanNamesForType(SpringLiquibase.class);

            assertThat(contexts).containsOnlyKeys(context.getId());
            assertThat(contextDescriptor).isNotNull();
            assertThat(contextDescriptor.getParentId()).isNull();
            assertThat(liquibaseBeanNames).hasSize(1);

            String liquibaseBeanName = liquibaseBeanNames[0];
            LiquibaseBeanDescriptor liquibaseBean = contextDescriptor.getLiquibaseBeans().get(liquibaseBeanName);

            assertThat(contextDescriptor.getLiquibaseBeans()).containsOnlyKeys(liquibaseBeanName);
            assertThat(liquibaseBean).isNotNull();

            List<ChangeSetDescriptor> changeSets = liquibaseBean.getChangeSets();
            assertThat(changeSets).hasSize(2);
            assertThat(changeSets).extracting(ChangeSetDescriptor::getId)
                    .containsExactly("create-endpoint-item", "insert-endpoint-item");
            assertThat(changeSets).extracting(ChangeSetDescriptor::getAuthor).containsOnly("endpoint-test");
            assertThat(changeSets).extracting(ChangeSetDescriptor::getChangeLog)
                    .containsOnly(changelog.toUri().toURL().toString());
            assertThat(changeSets).extracting(ChangeSetDescriptor::getExecType).containsOnly(ExecType.EXECUTED);
            assertThat(changeSets).allSatisfy((changeSet) -> {
                assertThat(changeSet.getChecksum()).isNotBlank();
                assertThat(changeSet.getDateExecuted()).isNotNull();
                assertThat(changeSet.getOrderExecuted()).isPositive();
            });
        }
    }

    private Path writeChangelog(String filename, String contents) throws Exception {
        Path changelog = this.tempDir.resolve(filename);
        Files.writeString(changelog, contents, StandardCharsets.UTF_8);
        return changelog;
    }

    private ConfigurableApplicationContext runApplication(Class<?> source, String... properties) {
        Map<String, Object> defaultProperties = new LinkedHashMap<>();
        defaultProperties.put("spring.main.banner-mode", "off");
        defaultProperties.put("spring.main.log-startup-info", "false");
        defaultProperties.put("spring.liquibase.analytics-enabled", "false");
        defaultProperties.put("spring.liquibase.show-summary", "off");
        defaultProperties.put("test.datasource.username", "sa");
        defaultProperties.put("test.datasource.password", "");
        for (String property : properties) {
            int separator = property.indexOf('=');
            defaultProperties.put(property.substring(0, separator), property.substring(separator + 1));
        }
        return new SpringApplicationBuilder(source).web(WebApplicationType.NONE).properties(defaultProperties).run();
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private void assertConnectionDetailsChangelogWasApplied(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT DESCRIPTION FROM CONNECTION_DETAIL_ITEM WHERE ID = 10")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("dedicated liquibase connection");
            assertThat(resultSet.next()).isFalse();
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class DataSourceApplication {

        @Bean
        DataSource dataSource(Environment environment) {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(environment.getRequiredProperty("test.datasource.url"));
            dataSource.setUser(environment.getProperty("test.datasource.username", "sa"));
            dataSource.setPassword(environment.getProperty("test.datasource.password", ""));
            return dataSource;
        }

    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class ConnectionDetailsApplication {

        @Bean
        DataSource dataSource(Environment environment) {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(environment.getRequiredProperty("test.datasource.url"));
            dataSource.setUser(environment.getProperty("test.datasource.username", "sa"));
            dataSource.setPassword(environment.getProperty("test.datasource.password", ""));
            return dataSource;
        }

        @Bean
        LiquibaseConnectionDetails liquibaseConnectionDetails(Environment environment) {
            return new H2LiquibaseConnectionDetails(environment.getRequiredProperty("test.liquibase.url"));
        }

    }

    private static final class H2LiquibaseConnectionDetails implements LiquibaseConnectionDetails {

        private final String jdbcUrl;

        private H2LiquibaseConnectionDetails(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        @Override
        public String getUsername() {
            return "sa";
        }

        @Override
        public String getPassword() {
            return "";
        }

        @Override
        public String getJdbcUrl() {
            return this.jdbcUrl;
        }

        @Override
        public String getDriverClassName() {
            return "org.h2.Driver";
        }

    }

}
