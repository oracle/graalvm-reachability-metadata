/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class JdbcJobExecutionDaoTest {
    @Test
    void restoresTypedJobParametersFromJdbcRepository() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:/org/springframework/batch/core/schema-hsqldb.sql")
                .build();
        try {
            JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
            factory.setDataSource(database);
            factory.setTransactionManager(new DataSourceTransactionManager(database));
            factory.afterPropertiesSet();
            JobRepository repository = factory.getObject();

            JobParameters parameters = new JobParameters(
                    Set.of(new JobParameter<>("sequence", 42L, Long.class)));
            JobInstance instance = repository.createJobInstance("parameterJob", parameters);
            JobExecution created = repository.createJobExecution(instance, parameters, new ExecutionContext());
            JobExecution restored = repository.getJobExecution(created.getId());

            assertNotNull(restored);
            assertEquals(42L, restored.getJobParameters().getLong("sequence"));
            assertEquals(Long.class, restored.getJobParameters().getParameter("sequence").type());
        } finally {
            database.shutdown();
        }
    }
}
