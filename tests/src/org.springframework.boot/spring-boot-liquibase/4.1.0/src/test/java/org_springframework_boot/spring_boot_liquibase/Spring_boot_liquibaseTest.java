/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_liquibase;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sql.DataSource;

import liquibase.changelog.ChangeSet;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.boot.liquibase.actuate.endpoint.LiquibaseEndpoint;
import org.springframework.boot.liquibase.autoconfigure.DataSourceClosingSpringLiquibase;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseConnectionDetails;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseDataSource;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseEndpointAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Spring_boot_liquibaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void liquibasePropertiesExposeDocumentedDefaultsAndAllSetters() {
        LiquibaseProperties properties = new LiquibaseProperties();

        assertThat(properties.getChangeLog()).isEqualTo("classpath:/db/changelog/db.changelog-master.yaml");
        assertThat(properties.getDatabaseChangeLogTable()).isEqualTo("DATABASECHANGELOG");
        assertThat(properties.getDatabaseChangeLogLockTable()).isEqualTo("DATABASECHANGELOGLOCK");
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isDropFirst()).isFalse();
        assertThat(properties.isClearChecksums()).isFalse();

        File rollbackFile = temporaryDirectory.resolve("rollback.sql").toFile();
        properties.setChangeLog("classpath:/db/changelog/application.yaml");
        properties.setContexts(List.of("test", "integration"));
        properties.setDefaultSchema("application");
        properties.setLiquibaseSchema("liquibase_schema");
        properties.setLiquibaseTablespace("liquibase_tablespace");
        properties.setDatabaseChangeLogTable("CHANGE_LOG");
        properties.setDatabaseChangeLogLockTable("CHANGE_LOG_LOCK");
        properties.setDropFirst(true);
        properties.setClearChecksums(true);
        properties.setEnabled(false);
        properties.setUser("migration_user");
        properties.setPassword("secret");
        properties.setDriverClassName("org.h2.Driver");
        properties.setUrl("jdbc:h2:mem:liquibase-properties");
        properties.setLabelFilter(List.of("blue", "green"));
        properties.setParameters(Map.of("schema", "public", "owner", "test"));
        properties.setRollbackFile(rollbackFile);
        properties.setTestRollbackOnUpdate(true);
        properties.setTag("release-1");
        properties.setShowSummary(LiquibaseProperties.ShowSummary.VERBOSE);
        properties.setShowSummaryOutput(LiquibaseProperties.ShowSummaryOutput.ALL);
        properties.setUiService(LiquibaseProperties.UiService.LOGGER);
        properties.setAnalyticsEnabled(Boolean.FALSE);
        properties.setLicenseKey("license-key");

        assertThat(properties.getChangeLog()).isEqualTo("classpath:/db/changelog/application.yaml");
        assertThat(properties.getContexts()).containsExactly("test", "integration");
        assertThat(properties.getDefaultSchema()).isEqualTo("application");
        assertThat(properties.getLiquibaseSchema()).isEqualTo("liquibase_schema");
        assertThat(properties.getLiquibaseTablespace()).isEqualTo("liquibase_tablespace");
        assertThat(properties.getDatabaseChangeLogTable()).isEqualTo("CHANGE_LOG");
        assertThat(properties.getDatabaseChangeLogLockTable()).isEqualTo("CHANGE_LOG_LOCK");
        assertThat(properties.isDropFirst()).isTrue();
        assertThat(properties.isClearChecksums()).isTrue();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getUser()).isEqualTo("migration_user");
        assertThat(properties.getPassword()).isEqualTo("secret");
        assertThat(properties.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(properties.getUrl()).isEqualTo("jdbc:h2:mem:liquibase-properties");
        assertThat(properties.getLabelFilter()).containsExactly("blue", "green");
        assertThat(properties.getParameters()).containsEntry("schema", "public").containsEntry("owner", "test");
        assertThat(properties.getRollbackFile()).isEqualTo(rollbackFile);
        assertThat(properties.isTestRollbackOnUpdate()).isTrue();
        assertThat(properties.getTag()).isEqualTo("release-1");
        assertThat(properties.getShowSummary()).isEqualTo(LiquibaseProperties.ShowSummary.VERBOSE);
        assertThat(properties.getShowSummaryOutput()).isEqualTo(LiquibaseProperties.ShowSummaryOutput.ALL);
        assertThat(properties.getUiService()).isEqualTo(LiquibaseProperties.UiService.LOGGER);
        assertThat(properties.getAnalyticsEnabled()).isFalse();
        assertThat(properties.getLicenseKey()).isEqualTo("license-key");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setChangeLog(null))
                .withMessage("'changeLog' must not be null");
    }

    @Test
    void liquibaseConnectionDetailsUsesExplicitJdbcValuesAndOptionalDriverClassName() {
        LiquibaseConnectionDetails details = new LiquibaseConnectionDetails() {
            @Override
            public String getUsername() {
                return "liquibase";
            }

            @Override
            public String getPassword() {
                return "password";
            }

            @Override
            public String getJdbcUrl() {
                return "jdbc:h2:mem:connection-details";
            }
        };

        assertThat(details.getUsername()).isEqualTo("liquibase");
        assertThat(details.getPassword()).isEqualTo("password");
        assertThat(details.getJdbcUrl()).isEqualTo("jdbc:h2:mem:connection-details");
        assertThat(details.getDriverClassName()).isEqualTo("org.h2.Driver");
    }

    @Test
    void dataSourceClosingSpringLiquibaseRunsMigrationAndClosesDataSourceAfterMigration() throws Exception {
        Path changeLog = writeChangeLog("closing-migration.xml", "book-one", "Spring Native", "native");
        CloseableH2DataSource dataSource = new CloseableH2DataSource();
        DataSourceClosingSpringLiquibase liquibase = new DataSourceClosingSpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog.toUri().toString());
        liquibase.setContexts("native");
        liquibase.setAnalyticsEnabled(false);

        liquibase.afterPropertiesSet();

        assertThat(dataSource.isClosed()).isTrue();
        assertThat(readBookTitles(dataSource)).containsExactly("Spring Native");
    }

    @Test
    void dataSourceClosingSpringLiquibaseCanDeferCloseUntilDestroy() throws Exception {
        Path changeLog = writeChangeLog("destroy-migration.xml", "book-two", "Liquibase Destroy", null);
        CloseableH2DataSource dataSource = new CloseableH2DataSource();
        DataSourceClosingSpringLiquibase liquibase = new DataSourceClosingSpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog.toUri().toString());
        liquibase.setCloseDataSourceOnceMigrated(false);
        liquibase.setAnalyticsEnabled(false);

        liquibase.afterPropertiesSet();

        assertThat(dataSource.isClosed()).isFalse();
        assertThat(readBookTitles(dataSource)).containsExactly("Liquibase Destroy");

        liquibase.destroy();

        assertThat(dataSource.isClosed()).isTrue();
    }

    @Test
    void liquibaseAutoConfigurationUsesDedicatedJdbcUrlAndChangeLogParameters() throws Exception {
        Path changeLog = writeParameterizedChangeLog("auto-config-migration.xml");
        String migrationDatabaseName = "liquibase-auto-config-" + UUID.randomUUID();
        String migrationUrl = "jdbc:h2:mem:" + migrationDatabaseName + ";DB_CLOSE_DELAY=-1";
        CloseableH2DataSource applicationDataSource = new CloseableH2DataSource();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("liquibaseTest", Map.of(
                    "spring.liquibase.change-log", changeLog.toUri().toString(),
                    "spring.liquibase.url", migrationUrl,
                    "spring.liquibase.user", "sa",
                    "spring.liquibase.parameters.title", "Auto Configured",
                    "spring.liquibase.analytics-enabled", "false")));
            context.getBeanFactory().registerSingleton("dataSource", applicationDataSource);
            context.register(LiquibaseAutoConfiguration.class);

            context.refresh();

            SpringLiquibase liquibase = context.getBean(SpringLiquibase.class);
            assertThat(liquibase.getDataSource()).isNotSameAs(applicationDataSource);
            assertThat(hasBooksTable(applicationDataSource)).isFalse();
            assertThat(applicationDataSource.isClosed()).isFalse();
            assertThat(readBookTitles(liquibase.getDataSource())).containsExactly("Auto Configured");
        }
    }

    @Test
    void liquibaseAutoConfigurationPrefersLiquibaseDataSourceBeanOverApplicationDataSource() throws Exception {
        Path changeLog = writeChangeLog("qualified-datasource-migration.xml", "liquibase-datasource-change",
                "Qualified DataSource", null);
        CloseableH2DataSource applicationDataSource = new CloseableH2DataSource();
        CloseableH2DataSource migrationDataSource = new CloseableH2DataSource();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("liquibaseDataSourceTest",
                    Map.of("spring.liquibase.change-log", changeLog.toUri().toString(),
                            "spring.liquibase.analytics-enabled", "false")));
            context.registerBean("dataSource", CloseableH2DataSource.class, () -> applicationDataSource);
            context.registerBean("liquibaseDataSource", CloseableH2DataSource.class, () -> migrationDataSource,
                    (beanDefinition) -> ((AbstractBeanDefinition) beanDefinition)
                            .addQualifier(new AutowireCandidateQualifier(LiquibaseDataSource.class)));
            context.register(LiquibaseAutoConfiguration.class);

            context.refresh();

            SpringLiquibase liquibase = context.getBean(SpringLiquibase.class);
            assertThat(liquibase.getDataSource()).isSameAs(migrationDataSource);
            assertThat(hasBooksTable(applicationDataSource)).isFalse();
            assertThat(readBookTitles(migrationDataSource)).containsExactly("Qualified DataSource");
        }
    }

    @Test
    void liquibaseEndpointReportsExecutedChangeSetsFromCurrentAndParentContexts() throws Exception {
        Path parentChangeLog = writeChangeLog("parent-endpoint.xml", "parent-change", "Parent Context", null);
        Path childChangeLog = writeChangeLog("child-endpoint.xml", "child-change", "Child Context", "endpoint");
        CloseableH2DataSource parentDataSource = new CloseableH2DataSource();
        CloseableH2DataSource childDataSource = new CloseableH2DataSource();
        SpringLiquibase parentLiquibase = migratedLiquibase(parentDataSource, parentChangeLog, null);
        SpringLiquibase childLiquibase = migratedLiquibase(childDataSource, childChangeLog, "endpoint");

        try (StaticApplicationContext parent = new StaticApplicationContext();
                StaticApplicationContext child = new StaticApplicationContext()) {
            parent.setId("parent-context");
            parent.getBeanFactory().registerSingleton("parentLiquibase", parentLiquibase);
            parent.refresh();
            child.setId("child-context");
            child.setParent(parent);
            child.getBeanFactory().registerSingleton("childLiquibase", childLiquibase);
            child.refresh();

            LiquibaseEndpoint.LiquibaseBeansDescriptor descriptor = new LiquibaseEndpoint(child).liquibaseBeans();

            assertThat(descriptor.getContexts()).containsOnlyKeys("child-context", "parent-context");
            LiquibaseEndpoint.ContextLiquibaseBeansDescriptor childContext = descriptor.getContexts()
                    .get("child-context");
            assertThat(childContext.getParentId()).isEqualTo("parent-context");
            assertThat(childContext.getLiquibaseBeans()).containsOnlyKeys("childLiquibase");
            LiquibaseEndpoint.ContextLiquibaseBeansDescriptor parentContext = descriptor.getContexts()
                    .get("parent-context");
            assertThat(parentContext.getParentId()).isNull();
            assertThat(parentContext.getLiquibaseBeans()).containsOnlyKeys("parentLiquibase");

            LiquibaseEndpoint.ChangeSetDescriptor childChangeSet = childContext.getLiquibaseBeans()
                    .get("childLiquibase").getChangeSets().get(0);
            assertThat(childChangeSet.getId()).isEqualTo("child-change");
            assertThat(childChangeSet.getAuthor()).isEqualTo("test");
            assertThat(childChangeSet.getExecType()).isEqualTo(ChangeSet.ExecType.EXECUTED);
            assertThat(childChangeSet.getContexts()).containsExactly("endpoint");
            assertThat(childChangeSet.getLabels()).isEmpty();
            assertThat(childChangeSet.getDateExecuted()).isNotNull();
            assertThat(childChangeSet.getDeploymentId()).isNotBlank();
            assertThat(childChangeSet.getOrderExecuted()).isPositive();
            assertThat(childChangeSet.getChecksum()).isNotBlank();
            assertThat(childChangeSet.getTag()).isNull();

            LiquibaseEndpoint.ChangeSetDescriptor parentChangeSet = parentContext.getLiquibaseBeans()
                    .get("parentLiquibase").getChangeSets().get(0);
            assertThat(parentChangeSet.getId()).isEqualTo("parent-change");
            assertThat(parentChangeSet.getContexts()).isEmpty();
        }
    }

    @Test
    void endpointValueDescriptorsReturnTheCollectionsTheyWereCreatedWith() {
        Set<String> contexts = Set.of("blue", "green");
        LiquibaseEndpoint.ContextExpressionDescriptor contextDescriptor =
                new LiquibaseEndpoint.ContextExpressionDescriptor(contexts);
        LiquibaseEndpoint.LiquibaseBeanDescriptor beanDescriptor = new LiquibaseEndpoint.LiquibaseBeanDescriptor(
                List.of());

        assertThat(contextDescriptor.getContexts()).isSameAs(contexts);
        assertThat(beanDescriptor.getChangeSets()).isEmpty();
    }

    @Test
    void publicAutoConfigurationTypesCanBeConstructed() {
        assertThat(new LiquibaseAutoConfiguration()).isNotNull();
        assertThat(new LiquibaseEndpointAutoConfiguration()).isNotNull();
    }

    private Path writeParameterizedChangeLog(String fileName) throws Exception {
        Path changeLog = temporaryDirectory.resolve(fileName);
        Files.writeString(changeLog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="parameterized-change" author="test">
                        <createTable tableName="books">
                            <column name="id" type="INT">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="title" type="VARCHAR(100)"/>
                        </createTable>
                        <insert tableName="books">
                            <column name="id" valueNumeric="1"/>
                            <column name="title" value="${title}"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """);
        return changeLog;
    }

    private Path writeChangeLog(String fileName, String changeSetId, String title, String context) throws Exception {
        String contextAttribute = (context != null) ? " contextFilter=\"" + context + "\"" : "";
        Path changeLog = temporaryDirectory.resolve(fileName);
        Files.writeString(changeLog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="%s" author="test"%s>
                        <createTable tableName="books">
                            <column name="id" type="INT">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="title" type="VARCHAR(100)"/>
                        </createTable>
                        <insert tableName="books">
                            <column name="id" valueNumeric="1"/>
                            <column name="title" value="%s"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """.formatted(changeSetId, contextAttribute, title));
        return changeLog;
    }

    private SpringLiquibase migratedLiquibase(CloseableH2DataSource dataSource, Path changeLog, String contexts)
            throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog.toUri().toString());
        liquibase.setDatabaseChangeLogTable("DATABASECHANGELOG");
        liquibase.setDatabaseChangeLogLockTable("DATABASECHANGELOGLOCK");
        liquibase.setAnalyticsEnabled(false);
        if (contexts != null) {
            liquibase.setContexts(contexts);
        }
        liquibase.afterPropertiesSet();
        return liquibase;
    }

    private List<String> readBookTitles(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT title FROM books ORDER BY id")) {
            return readTitles(resultSet);
        }
    }

    private boolean hasBooksTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet resultSet = connection.getMetaData().getTables(null, null, "BOOKS", null)) {
            return resultSet.next();
        }
    }

    private List<String> readTitles(ResultSet resultSet) throws SQLException {
        ArrayList<String> titles = new ArrayList<>();
        while (resultSet.next()) {
            titles.add(resultSet.getString("title"));
        }
        return titles;
    }

    public static final class CloseableH2DataSource implements DataSource {

        private final Driver driver = new Driver();

        private final String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

        private volatile boolean closed;

        @Override
        public Connection getConnection() throws SQLException {
            return getConnection("sa", "");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties properties = new Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            return this.driver.connect(this.url, properties);
        }

        public void close() {
            this.closed = true;
        }

        boolean isClosed() {
            return this.closed;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
