/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

@SuppressWarnings("removal")
public class JobRepositoryFactoryBeanTest {
    @Test
    void createsJdbcRepositoryWithConfiguredClobType() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:/org/springframework/batch/core/schema-hsqldb.sql")
                .build();
        try {
            JobRepositoryFactoryBean factory = repositoryFactory(database);

            factory.afterPropertiesSet();
            JobRepository repository = factory.getObject();

            assertNotNull(repository);
            assertTrue(repository.getJobNames().isEmpty());
        } finally {
            database.shutdown();
        }
    }

    private static JobRepositoryFactoryBean repositoryFactory(DataSource dataSource) {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(new DataSourceTransactionManager(dataSource));
        factory.setDatabaseType("HSQL");
        factory.setClobType(Types.CLOB);
        return factory;
    }
}
