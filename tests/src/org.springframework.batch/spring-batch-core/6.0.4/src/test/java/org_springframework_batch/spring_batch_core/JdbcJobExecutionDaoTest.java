/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.dao.jdbc.JdbcJobExecutionDao;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class JdbcJobExecutionDaoTest {
    @Test
    void readsTypedJobParametersFromJdbcStore() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:/org/springframework/batch/core/schema-hsqldb.sql")
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
            jdbcTemplate.update(
                    "INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) "
                            + "VALUES (?, ?, ?, ?)",
                    11L, 0L, "importJob", "importJobKey");
            jdbcTemplate.update(
                    "INSERT INTO BATCH_JOB_EXECUTION "
                            + "(JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME) "
                            + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    31L, 0L, 11L);
            jdbcTemplate.update(
                    "INSERT INTO BATCH_JOB_EXECUTION_PARAMS "
                            + "(JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, PARAMETER_VALUE, IDENTIFYING) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    31L, "limit", Integer.class.getName(), "25", "N");
            JdbcJobExecutionDao dao = new JdbcJobExecutionDao();
            dao.setJdbcTemplate(jdbcTemplate);
            dao.setConversionService(new DefaultConversionService());

            JobParameters parameters = dao.getJobParameters(31L);
            JobParameter<?> parameter = parameters.getParameter("limit");

            assertEquals(25, parameter.value());
            assertEquals(Integer.class, parameter.type());
            assertFalse(parameter.identifying());
        } finally {
            database.shutdown();
        }
    }
}
