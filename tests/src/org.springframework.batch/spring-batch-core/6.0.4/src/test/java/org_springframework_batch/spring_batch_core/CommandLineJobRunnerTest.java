/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.CommandLineJobRunner;
import org.springframework.batch.core.launch.support.SystemExiter;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@SuppressWarnings("removal")
public class CommandLineJobRunnerTest {
    private static final AtomicInteger TASKLET_RUNS = new AtomicInteger();

    @Test
    void launchesConfigurationClassAndNamedJob() throws Exception {
        CapturingSystemExiter systemExiter = new CapturingSystemExiter();
        TASKLET_RUNS.set(0);
        CommandLineJobRunner.presetSystemExiter(systemExiter);

        CommandLineJobRunner.main(
                new String[] {BatchConfiguration.class.getName(), "sampleJob", "request=coverage"});

        assertEquals(0, systemExiter.exitCode);
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
        public JobLauncher jobLauncher(JobRepository repository) throws Exception {
            TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
            launcher.setJobRepository(repository);
            launcher.afterPropertiesSet();
            return launcher;
        }

        @Bean
        public Job sampleJob(JobRepository repository, PlatformTransactionManager transactionManager) {
            Step step = new StepBuilder("sampleStep", repository)
                    .tasklet(new CountingTasklet(), transactionManager)
                    .build();
            return new JobBuilder("sampleJob", repository).start(step).build();
        }
    }

    private static final class CapturingSystemExiter implements SystemExiter {
        private int exitCode = -1;

        @Override
        public void exit(int status) {
            exitCode = status;
        }
    }

    private static final class CountingTasklet implements Tasklet {
        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
            TASKLET_RUNS.incrementAndGet();
            return RepeatStatus.FINISHED;
        }
    }
}
