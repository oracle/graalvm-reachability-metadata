/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_liquibase;

import liquibase.changelog.ChangeSet.ExecType;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import org.springframework.boot.liquibase.actuate.endpoint.LiquibaseEndpoint;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_liquibaseTest {

    @Test
    void endpointReportsExecutedChangeSets() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:liquibase-endpoint;DB_CLOSE_DELAY=-1");

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/endpoint-changelog.xml");
        liquibase.setDatabaseChangeLogTable("DATABASECHANGELOG");
        liquibase.setDatabaseChangeLogLockTable("DATABASECHANGELOGLOCK");

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("liquibase", SpringLiquibase.class, () -> liquibase);
            context.refresh();

            LiquibaseEndpoint.LiquibaseBeansDescriptor descriptor = new LiquibaseEndpoint(context)
                    .liquibaseBeans();
            LiquibaseEndpoint.ContextLiquibaseBeansDescriptor contextDescriptor =
                    descriptor.getContexts().get(context.getId());
            LiquibaseEndpoint.ChangeSetDescriptor changeSet = contextDescriptor.getLiquibaseBeans()
                    .get("liquibase")
                    .getChangeSets()
                    .get(0);

            assertThat(changeSet.getId()).isEqualTo("create-account");
            assertThat(changeSet.getAuthor()).isEqualTo("test");
            assertThat(changeSet.getExecType()).isEqualTo(ExecType.EXECUTED);
            assertThat(changeSet.getChangeLog()).endsWith("endpoint-changelog.xml");
        }
    }

}
