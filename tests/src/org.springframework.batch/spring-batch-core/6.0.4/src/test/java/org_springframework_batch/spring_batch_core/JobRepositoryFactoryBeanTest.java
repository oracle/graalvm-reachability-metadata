/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Types;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class JobRepositoryFactoryBeanTest {
    @Test
    void createsJdbcRepositoryWithConfiguredClobType() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:/org/springframework/batch/core/schema-hsqldb.sql")
                .build();
        try {
            JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
            factory.setDataSource(database);
            factory.setTransactionManager(new DataSourceTransactionManager(database));
            factory.setClobType(Types.CLOB);
            factory.afterPropertiesSet();

            JobRepository repository = factory.getObject();
            JobParameters parameters = new JobParameters(
                    Set.of(new JobParameter<>("request", "factory", String.class)));
            JobInstance created = repository.createJobInstance("factoryJob", parameters);
            JobInstance restored = repository.getJobInstance(created.getInstanceId());

            assertNotNull(restored);
            assertEquals("factoryJob", restored.getJobName());
        } finally {
            database.shutdown();
        }
    }
}
