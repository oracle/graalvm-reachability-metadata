/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.CommandLineJobOperator;
import org.springframework.batch.core.launch.support.ExitCodeMapper;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@SuppressWarnings("removal")
public class CommandLineJobOperatorTest {
    private static final AtomicInteger TASKLET_RUNS = new AtomicInteger();

    @Test
    void loadsConfigurationAndStartsNamedJob() {
        TASKLET_RUNS.set(0);

        ExitCodeObserved result = assertThrows(
                ExitCodeObserved.class,
                () -> CommandLineJobOperator.main(new String[] {
                    BatchConfiguration.class.getName(), "start", "sampleJob", "request=coverage"
                }));

        assertEquals("COMPLETED", result.exitCode);
        assertEquals(1, TASKLET_RUNS.get());
    }

    @Configuration(proxyBeanMethods = false)
    public static class BatchConfiguration {
        @Bean
        public JobRepository jobRepository() {
            return new ResourcelessJobRepository();
        }

        @Bean
        public PlatformTransactionManager transactionManager() {
            return new ResourcelessTransactionManager();
        }

        @Bean
        public JobRegistry jobRegistry() {
            return new MapJobRegistry();
        }

        @Bean
        public JobOperator jobOperator(JobRepository repository, JobRegistry registry) throws Exception {
            TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
            operator.setJobRepository(repository);
            operator.setJobRegistry(registry);
            operator.afterPropertiesSet();
            return operator;
        }

        @Bean
        public Job sampleJob(JobRepository repository, PlatformTransactionManager transactionManager) {
            Step step = new StepBuilder("sampleStep", repository)
                    .tasklet(new CountingTasklet(), transactionManager)
                    .build();
            return new JobBuilder("sampleJob", repository).start(step).build();
        }

        @Bean
        public ExitCodeMapper exitCodeMapper(ConfigurableApplicationContext context) {
            return new ObservingExitCodeMapper(context);
        }
    }

    private static final class CountingTasklet implements Tasklet {
        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
            TASKLET_RUNS.incrementAndGet();
            return RepeatStatus.FINISHED;
        }
    }

    private static final class ObservingExitCodeMapper implements ExitCodeMapper {
        private final ConfigurableApplicationContext context;

        private ObservingExitCodeMapper(ConfigurableApplicationContext context) {
            this.context = context;
        }

        @Override
        public int intValue(String exitCode) {
            context.close();
            throw new ExitCodeObserved(exitCode);
        }
    }

    private static final class ExitCodeObserved extends Error {
        private static final long serialVersionUID = 1L;

        private final String exitCode;

        private ExitCodeObserved(String exitCode) {
            this.exitCode = exitCode;
        }
    }
}
