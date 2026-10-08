/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_batch;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchProperties;
import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.boot.batch.autoconfigure.JobExecutionEvent;
import org.springframework.boot.batch.autoconfigure.JobExecutionExitCodeGenerator;
import org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.task.TaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_batchTest {

    @Test
    void autoConfigurationLaunchesSelectedJobWithBoundPropertiesAndArguments() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("batch-test",
                Map.of("spring.batch.job.name", "selectedJob")));
        context.register(BatchIntegrationConfiguration.class);

        try {
            context.refresh();

            BatchProperties properties = context.getBean(BatchProperties.class);
            JobLauncherApplicationRunner runner = context.getBean(JobLauncherApplicationRunner.class);
            runner.run("run.id=41,java.lang.Long,true", "customer=alice");

            ExecutionCapture capture = context.getBean(ExecutionCapture.class);
            assertThat(properties.getJob().getName()).isEqualTo("selectedJob");
            assertThat(capture.selectedJobRan).isTrue();
            assertThat(capture.skippedJobRan).isFalse();
            assertThat(capture.executorInvocations).hasValue(1);
            assertThat(capture.parameters.getLong("run.id")).isEqualTo(41L);
            assertThat(capture.parameters.getString("customer")).isEqualTo("alice");

            JobRepository repository = context.getBean(JobRepository.class);
            JobExecution execution = repository.getLastJobExecution("selectedJob", capture.parameters);
            assertThat(execution).isNotNull();
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            assertThat(context.getBean(JobExecutionExitCodeGenerator.class).getExitCode())
                    .isEqualTo(BatchStatus.COMPLETED.ordinal());
        }
        finally {
            context.close();
        }
    }

    @Test
    void exitCodeGeneratorReflectsPublishedJobExecutionStatus() {
        JobExecution execution = new JobExecution(7L, new JobInstance(3L, "failedJob"), new JobParameters());
        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(ExitStatus.FAILED);
        JobExecutionEvent event = new JobExecutionEvent(execution);
        JobExecutionExitCodeGenerator generator = new JobExecutionExitCodeGenerator();

        generator.onApplicationEvent(event);

        assertThat(event.getJobExecution()).isSameAs(execution);
        assertThat(generator.getExitCode()).isEqualTo(BatchStatus.FAILED.ordinal());
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({ BatchAutoConfiguration.class, BatchJobLauncherAutoConfiguration.class })
    static class BatchIntegrationConfiguration {

        @Bean
        ExecutionCapture executionCapture() {
            return new ExecutionCapture();
        }

        @Bean
        @BatchTaskExecutor
        TaskExecutor batchTaskExecutor(ExecutionCapture capture) {
            return new RecordingTaskExecutor(capture);
        }

        @Bean
        Step selectedStep(JobRepository repository, ExecutionCapture capture) {
            return new StepBuilder("selectedStep", repository).tasklet(new SelectedJobTasklet(capture)).build();
        }

        @Bean
        Step skippedStep(JobRepository repository, ExecutionCapture capture) {
            return new StepBuilder("skippedStep", repository).tasklet(new SkippedJobTasklet(capture)).build();
        }

        @Bean
        Job selectedJob(JobRepository repository, @Qualifier("selectedStep") Step selectedStep) {
            return new JobBuilder("selectedJob", repository).start(selectedStep).build();
        }

        @Bean
        Job skippedJob(JobRepository repository, @Qualifier("skippedStep") Step skippedStep) {
            return new JobBuilder("skippedJob", repository).start(skippedStep).build();
        }

    }

    static final class ExecutionCapture {

        private final AtomicBoolean selectedJobRan = new AtomicBoolean();

        private final AtomicBoolean skippedJobRan = new AtomicBoolean();

        private final AtomicInteger executorInvocations = new AtomicInteger();

        private JobParameters parameters;

    }

    static final class RecordingTaskExecutor implements TaskExecutor {

        private final ExecutionCapture capture;

        RecordingTaskExecutor(ExecutionCapture capture) {
            this.capture = capture;
        }

        @Override
        public void execute(Runnable task) {
            this.capture.executorInvocations.incrementAndGet();
            task.run();
        }

    }

    static final class SelectedJobTasklet implements Tasklet {

        private final ExecutionCapture capture;

        SelectedJobTasklet(ExecutionCapture capture) {
            this.capture = capture;
        }

        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
            this.capture.selectedJobRan.set(true);
            this.capture.parameters = contribution.getStepExecution().getJobExecution().getJobParameters();
            return RepeatStatus.FINISHED;
        }

    }

    static final class SkippedJobTasklet implements Tasklet {

        private final ExecutionCapture capture;

        SkippedJobTasklet(ExecutionCapture capture) {
            this.capture = capture;
        }

        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
            this.capture.skippedJobRan.set(true);
            return RepeatStatus.FINISHED;
        }

    }

}
