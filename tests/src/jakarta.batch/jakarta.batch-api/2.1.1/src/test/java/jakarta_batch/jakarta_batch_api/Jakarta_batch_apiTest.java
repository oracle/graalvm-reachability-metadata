/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_batch.jakarta_batch_api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.batch.api.AbstractBatchlet;
import jakarta.batch.api.Decider;
import jakarta.batch.api.chunk.AbstractCheckpointAlgorithm;
import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.batch.api.chunk.ItemProcessor;
import jakarta.batch.api.chunk.ItemReader;
import jakarta.batch.api.chunk.listener.AbstractChunkListener;
import jakarta.batch.api.chunk.listener.AbstractItemProcessListener;
import jakarta.batch.api.chunk.listener.AbstractItemReadListener;
import jakarta.batch.api.chunk.listener.AbstractItemWriteListener;
import jakarta.batch.api.chunk.listener.RetryProcessListener;
import jakarta.batch.api.chunk.listener.RetryReadListener;
import jakarta.batch.api.chunk.listener.RetryWriteListener;
import jakarta.batch.api.chunk.listener.SkipProcessListener;
import jakarta.batch.api.chunk.listener.SkipReadListener;
import jakarta.batch.api.chunk.listener.SkipWriteListener;
import jakarta.batch.api.listener.AbstractJobListener;
import jakarta.batch.api.listener.AbstractStepListener;
import jakarta.batch.api.partition.AbstractPartitionAnalyzer;
import jakarta.batch.api.partition.AbstractPartitionReducer;
import jakarta.batch.api.partition.PartitionCollector;
import jakarta.batch.api.partition.PartitionMapper;
import jakarta.batch.api.partition.PartitionPlanImpl;
import jakarta.batch.api.partition.PartitionReducer;
import jakarta.batch.operations.BatchRuntimeException;
import jakarta.batch.operations.JobExecutionAlreadyCompleteException;
import jakarta.batch.operations.JobExecutionIsRunningException;
import jakarta.batch.operations.JobExecutionNotMostRecentException;
import jakarta.batch.operations.JobExecutionNotRunningException;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.operations.JobRestartException;
import jakarta.batch.operations.JobSecurityException;
import jakarta.batch.operations.JobStartException;
import jakarta.batch.operations.NoSuchJobException;
import jakarta.batch.operations.NoSuchJobExecutionException;
import jakarta.batch.operations.NoSuchJobInstanceException;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.BatchStatus;
import jakarta.batch.runtime.JobExecution;
import jakarta.batch.runtime.JobInstance;
import jakarta.batch.runtime.Metric;
import jakarta.batch.runtime.StepExecution;
import jakarta.batch.runtime.context.JobContext;
import jakarta.batch.runtime.context.StepContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jakarta_batch_apiTest {
    @Test
    void batchletAndChunkBaseClassesProvideUsableNoOpDefaults() throws Exception {
        CountingBatchlet batchlet = new CountingBatchlet("finished");
        assertThat(batchlet.process()).isEqualTo("finished");
        batchlet.stop();
        assertThat(batchlet.wasStopped()).isTrue();

        ItemReader reader = new ListItemReader(List.of("a", "b"));
        reader.open("checkpoint");
        assertThat(reader.checkpointInfo()).isNull();
        assertThat(reader.readItem()).isEqualTo("a");
        assertThat(reader.readItem()).isEqualTo("b");
        assertThat(reader.readItem()).isNull();
        reader.close();

        CapturingItemWriter writer = new CapturingItemWriter();
        writer.open(null);
        writer.writeItems(List.of("x", "y"));
        assertThat(writer.checkpointInfo()).isNull();
        writer.close();
        assertThat(writer.items()).containsExactly("x", "y");

        ThresholdCheckpointAlgorithm checkpointAlgorithm = new ThresholdCheckpointAlgorithm(2);
        assertThat(checkpointAlgorithm.checkpointTimeout()).isZero();
        checkpointAlgorithm.beginCheckpoint();
        assertThat(checkpointAlgorithm.isReadyToCheckpoint()).isFalse();
        assertThat(checkpointAlgorithm.isReadyToCheckpoint()).isTrue();
        checkpointAlgorithm.endCheckpoint();
    }

    @Test
    void listenersCanBeInvokedThroughTheirPublicContracts() throws Exception {
        AbstractJobListener jobListener = new RecordingJobListener();
        jobListener.beforeJob();
        jobListener.afterJob();
        assertThat(((RecordingJobListener) jobListener).events()).containsExactly("beforeJob", "afterJob");

        AbstractStepListener stepListener = new RecordingStepListener();
        stepListener.beforeStep();
        stepListener.afterStep();
        assertThat(((RecordingStepListener) stepListener).events()).containsExactly("beforeStep", "afterStep");

        Exception failure = new Exception("chunk failure");
        AbstractChunkListener chunkListener = new RecordingChunkListener();
        chunkListener.beforeChunk();
        chunkListener.onError(failure);
        chunkListener.afterChunk();
        assertThat(((RecordingChunkListener) chunkListener).events())
                .containsExactly("beforeChunk", "error:chunk failure", "afterChunk");

        AbstractItemReadListener readListener = new RecordingItemReadListener();
        readListener.beforeRead();
        readListener.afterRead("record");
        readListener.onReadError(new Exception("read failed"));
        assertThat(((RecordingItemReadListener) readListener).events())
                .containsExactly("beforeRead", "afterRead:record", "readError:read failed");

        AbstractItemProcessListener processListener = new RecordingItemProcessListener();
        processListener.beforeProcess("input");
        processListener.afterProcess("input", "output");
        processListener.onProcessError("input", new Exception("process failed"));
        assertThat(((RecordingItemProcessListener) processListener).events())
                .containsExactly("beforeProcess:input", "afterProcess:input->output", "processError:process failed");

        AbstractItemWriteListener writeListener = new RecordingItemWriteListener();
        writeListener.beforeWrite(List.of("one"));
        writeListener.afterWrite(List.of("two"));
        writeListener.onWriteError(List.of("three"), new Exception("write failed"));
        assertThat(((RecordingItemWriteListener) writeListener).events())
                .containsExactly("beforeWrite:one", "afterWrite:two", "writeError:write failed");
    }

    @Test
    void retryAndSkipListenerInterfacesReceiveFailuresAndItems() throws Exception {
        RecordingRetryAndSkipListener listener = new RecordingRetryAndSkipListener();

        listener.onRetryReadException(new Exception("retry read"));
        listener.onRetryProcessException("process item", new Exception("retry process"));
        listener.onRetryWriteException(List.of("write item"), new Exception("retry write"));
        listener.onSkipReadItem(new Exception("skip read"));
        listener.onSkipProcessItem("skipped item", new Exception("skip process"));
        listener.onSkipWriteItem(List.of("skipped write"), new Exception("skip write"));

        assertThat(listener.events()).containsExactly(
                "retryRead:retry read",
                "retryProcess:process item:retry process",
                "retryWrite:write item:retry write",
                "skipRead:skip read",
                "skipProcess:skipped item:skip process",
                "skipWrite:skipped write:skip write");
    }

    @Test
    void partitionPlanAndPartitionContractsCoordinatePartitionResults() throws Exception {
        PartitionPlanImpl plan = new PartitionPlanImpl();
        assertThat(plan.getPartitions()).isZero();
        assertThat(plan.getThreads()).isZero();
        assertThat(plan.getPartitionsOverride()).isFalse();
        assertThat(plan.getPartitionProperties()).isNull();

        plan.setPartitions(3);
        assertThat(plan.getPartitions()).isEqualTo(3);
        assertThat(plan.getThreads()).isEqualTo(3);

        plan.setThreads(2);
        plan.setPartitionsOverride(true);
        Properties firstPartitionProperties = new Properties();
        firstPartitionProperties.setProperty("partition", "first");
        Properties secondPartitionProperties = new Properties();
        secondPartitionProperties.setProperty("partition", "second");
        plan.setPartitionProperties(new Properties[] {firstPartitionProperties, secondPartitionProperties});

        assertThat(plan.getThreads()).isEqualTo(2);
        assertThat(plan.getPartitionsOverride()).isTrue();
        assertThat(plan.getPartitionProperties())
                .extracting(properties -> properties.getProperty("partition"))
                .containsExactly("first", "second");

        PartitionMapper mapper = () -> plan;
        assertThat(mapper.mapPartitions()).isSameAs(plan);

        PartitionCollector collector = () -> "collector-data";
        assertThat(collector.collectPartitionData()).isEqualTo("collector-data");

        RecordingPartitionAnalyzer analyzer = new RecordingPartitionAnalyzer();
        analyzer.analyzeCollectorData(collector.collectPartitionData());
        analyzer.analyzeStatus(BatchStatus.COMPLETED, "partition complete");
        assertThat(analyzer.events()).containsExactly("data:collector-data", "status:COMPLETED:partition complete");

        RecordingPartitionReducer reducer = new RecordingPartitionReducer();
        reducer.beginPartitionedStep();
        reducer.beforePartitionedStepCompletion();
        reducer.afterPartitionedStepCompletion(PartitionReducer.PartitionStatus.COMMIT);
        reducer.rollbackPartitionedStep();
        assertThat(reducer.events()).containsExactly("begin", "beforeCompletion", "after:COMMIT", "rollback");
    }

    @Test
    void itemProcessorDeciderRuntimeContextsAndExecutionsExposeTypedBatchState() throws Exception {
        ItemProcessor processor = item -> String.valueOf(item).toUpperCase();
        assertThat(processor.processItem("payload")).isEqualTo("PAYLOAD");

        StepExecution firstStep = new SimpleStepExecution(10L, "load", BatchStatus.COMPLETED, "loaded", "reader-state");
        StepExecution secondStep = new SimpleStepExecution(11L, "write", BatchStatus.FAILED, "writer-error", null);
        Decider decider = executions -> executions[executions.length - 1].getBatchStatus().name();
        assertThat(decider.decide(new StepExecution[] {firstStep, secondStep})).isEqualTo("FAILED");

        Metric readMetric = new SimpleMetric(Metric.MetricType.READ_COUNT, 42L);
        Metric writeMetric = new SimpleMetric(Metric.MetricType.WRITE_COUNT, 40L);
        StepContext stepContext = new SimpleStepContext(firstStep, new Metric[] {readMetric, writeMetric});
        stepContext.setTransientUserData("step-transient");
        stepContext.setPersistentUserData("step-persistent");
        stepContext.setExitStatus("step-exit");

        assertThat(stepContext.getStepName()).isEqualTo("load");
        assertThat(stepContext.getStepExecutionId()).isEqualTo(10L);
        assertThat(stepContext.getBatchStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepContext.getTransientUserData()).isEqualTo("step-transient");
        assertThat(stepContext.getPersistentUserData()).isEqualTo("step-persistent");
        assertThat(stepContext.getExitStatus()).isEqualTo("step-exit");
        assertThat(stepContext.getException()).isNull();
        assertThat(stepContext.getMetrics()).extracting(Metric::getValue).containsExactly(42L, 40L);

        Properties jobProperties = new Properties();
        jobProperties.setProperty("input", "orders.csv");
        JobContext jobContext = new SimpleJobContext("nightly-job", 100L, 200L, jobProperties);
        jobContext.setTransientUserData("job-transient");
        jobContext.setExitStatus("job-exit");

        assertThat(jobContext.getJobName()).isEqualTo("nightly-job");
        assertThat(jobContext.getInstanceId()).isEqualTo(100L);
        assertThat(jobContext.getExecutionId()).isEqualTo(200L);
        assertThat(jobContext.getBatchStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(jobContext.getProperties()).containsEntry("input", "orders.csv");
        assertThat(jobContext.getTransientUserData()).isEqualTo("job-transient");
        assertThat(jobContext.getExitStatus()).isEqualTo("job-exit");

        assertThat(BatchStatus.values()).containsExactly(
                BatchStatus.STARTING,
                BatchStatus.STARTED,
                BatchStatus.STOPPING,
                BatchStatus.STOPPED,
                BatchStatus.FAILED,
                BatchStatus.COMPLETED,
                BatchStatus.ABANDONED);
        assertThat(Metric.MetricType.values()).containsExactly(
                Metric.MetricType.READ_COUNT,
                Metric.MetricType.WRITE_COUNT,
                Metric.MetricType.COMMIT_COUNT,
                Metric.MetricType.ROLLBACK_COUNT,
                Metric.MetricType.READ_SKIP_COUNT,
                Metric.MetricType.PROCESS_SKIP_COUNT,
                Metric.MetricType.FILTER_COUNT,
                Metric.MetricType.WRITE_SKIP_COUNT);
    }

    @Test
    void jobOperatorInterfaceSupportsStartingQueryingStoppingAndAbandoningJobs() throws Exception {
        InMemoryJobOperator operator = new InMemoryJobOperator();
        Properties parameters = new Properties();
        parameters.setProperty("date", "2026-05-09");

        long executionId = operator.start("dailyJob", parameters);
        assertThat(operator.getJobNames()).containsExactly("dailyJob");
        assertThat(operator.getJobInstanceCount("dailyJob")).isEqualTo(1);
        assertThat(operator.getRunningExecutions("dailyJob")).containsExactly(executionId);
        assertThat(operator.getParameters(executionId)).containsEntry("date", "2026-05-09");

        JobInstance instance = operator.getJobInstance(executionId);
        assertThat(instance.getInstanceId()).isEqualTo(1L);
        assertThat(instance.getJobName()).isEqualTo("dailyJob");
        assertThat(operator.getJobInstances("dailyJob", 0, 10)).containsExactly(instance);

        JobExecution execution = operator.getJobExecution(executionId);
        assertThat(execution.getExecutionId()).isEqualTo(executionId);
        assertThat(execution.getJobName()).isEqualTo("dailyJob");
        assertThat(execution.getBatchStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(execution.getJobParameters()).containsEntry("date", "2026-05-09");
        assertThat(operator.getJobExecutions(instance)).containsExactly(execution);
        assertThat(operator.getStepExecutions(executionId))
                .extracting(StepExecution::getStepName)
                .containsExactly("dailyJob.step");

        operator.stop(executionId);
        assertThat(operator.getJobExecution(executionId).getBatchStatus()).isEqualTo(BatchStatus.STOPPED);
        assertThat(operator.getRunningExecutions("dailyJob")).isEmpty();
        assertThat(operator.restart(executionId, new Properties())).isEqualTo(executionId + 1);

        operator.abandon(executionId);
        assertThat(operator.getJobExecution(executionId).getBatchStatus()).isEqualTo(BatchStatus.ABANDONED);
        assertThatThrownBy(() -> operator.getJobInstance(999L)).isInstanceOf(NoSuchJobExecutionException.class);
        assertThatThrownBy(() -> operator.getJobInstanceCount("missingJob")).isInstanceOf(NoSuchJobException.class);
    }

    @Test
    void batchRuntimeReturnsNoOperatorWhenNoServiceProviderIsPresent() {
        assertThat(BatchRuntime.getJobOperator()).isNull();
    }

    @Test
    void batchRuntimeExceptionsPreserveMessagesAndCauses() {
        verifyRuntimeExceptionConstructors(
                BatchRuntimeException::new,
                BatchRuntimeException::new,
                BatchRuntimeException::new,
                BatchRuntimeException::new);
        verifyRuntimeExceptionConstructors(
                JobExecutionAlreadyCompleteException::new,
                JobExecutionAlreadyCompleteException::new,
                JobExecutionAlreadyCompleteException::new,
                JobExecutionAlreadyCompleteException::new);
        verifyRuntimeExceptionConstructors(
                JobExecutionIsRunningException::new,
                JobExecutionIsRunningException::new,
                JobExecutionIsRunningException::new,
                JobExecutionIsRunningException::new);
        verifyRuntimeExceptionConstructors(
                JobExecutionNotMostRecentException::new,
                JobExecutionNotMostRecentException::new,
                JobExecutionNotMostRecentException::new,
                JobExecutionNotMostRecentException::new);
        verifyRuntimeExceptionConstructors(
                JobExecutionNotRunningException::new,
                JobExecutionNotRunningException::new,
                JobExecutionNotRunningException::new,
                JobExecutionNotRunningException::new);
        verifyRuntimeExceptionConstructors(
                JobRestartException::new,
                JobRestartException::new,
                JobRestartException::new,
                JobRestartException::new);
        verifyRuntimeExceptionConstructors(
                JobSecurityException::new,
                JobSecurityException::new,
                JobSecurityException::new,
                JobSecurityException::new);
        verifyRuntimeExceptionConstructors(
                JobStartException::new,
                JobStartException::new,
                JobStartException::new,
                JobStartException::new);
        verifyRuntimeExceptionConstructors(
                NoSuchJobException::new,
                NoSuchJobException::new,
                NoSuchJobException::new,
                NoSuchJobException::new);
        verifyRuntimeExceptionConstructors(
                NoSuchJobExecutionException::new,
                NoSuchJobExecutionException::new,
                NoSuchJobExecutionException::new,
                NoSuchJobExecutionException::new);
        verifyRuntimeExceptionConstructors(
                NoSuchJobInstanceException::new,
                NoSuchJobInstanceException::new,
                NoSuchJobInstanceException::new,
                NoSuchJobInstanceException::new);
    }

    private static void verifyRuntimeExceptionConstructors(
            Supplier<? extends BatchRuntimeException> noArgumentConstructor,
            Function<String, ? extends BatchRuntimeException> messageConstructor,
            Function<Throwable, ? extends BatchRuntimeException> causeConstructor,
            BiFunction<String, Throwable, ? extends BatchRuntimeException> messageAndCauseConstructor) {
        RuntimeException cause = new RuntimeException("cause");

        assertThat(noArgumentConstructor.get()).hasMessage(null).hasNoCause();
        assertThat(messageConstructor.apply("message")).hasMessage("message").hasNoCause();
        assertThat(causeConstructor.apply(cause)).hasCause(cause);
        assertThat(messageAndCauseConstructor.apply("message", cause)).hasMessage("message").hasCause(cause);
    }

    private static final class CountingBatchlet extends AbstractBatchlet {
        private final String exitStatus;
        private boolean stopped;

        private CountingBatchlet(String exitStatus) {
            this.exitStatus = exitStatus;
        }

        @Override
        public String process() {
            return exitStatus;
        }

        @Override
        public void stop() throws Exception {
            super.stop();
            stopped = true;
        }

        private boolean wasStopped() {
            return stopped;
        }
    }

    private static final class ListItemReader extends AbstractItemReader {
        private final List<?> items;
        private int index;

        private ListItemReader(List<?> items) {
            this.items = items;
        }

        @Override
        public Object readItem() {
            if (index >= items.size()) {
                return null;
            }
            return items.get(index++);
        }
    }

    private static final class CapturingItemWriter extends AbstractItemWriter {
        private final List<Object> items = new ArrayList<>();

        @Override
        public void writeItems(List<Object> items) {
            this.items.addAll(items);
        }

        private List<Object> items() {
            return Collections.unmodifiableList(items);
        }
    }

    private static final class ThresholdCheckpointAlgorithm extends AbstractCheckpointAlgorithm {
        private final int threshold;
        private int invocations;

        private ThresholdCheckpointAlgorithm(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public boolean isReadyToCheckpoint() {
            invocations++;
            return invocations >= threshold;
        }
    }

    private static final class RecordingJobListener extends AbstractJobListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeJob() {
            events.add("beforeJob");
        }

        @Override
        public void afterJob() {
            events.add("afterJob");
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingStepListener extends AbstractStepListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeStep() {
            events.add("beforeStep");
        }

        @Override
        public void afterStep() {
            events.add("afterStep");
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingChunkListener extends AbstractChunkListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeChunk() {
            events.add("beforeChunk");
        }

        @Override
        public void onError(Exception ex) {
            events.add("error:" + ex.getMessage());
        }

        @Override
        public void afterChunk() {
            events.add("afterChunk");
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingItemReadListener extends AbstractItemReadListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeRead() {
            events.add("beforeRead");
        }

        @Override
        public void afterRead(Object item) {
            events.add("afterRead:" + item);
        }

        @Override
        public void onReadError(Exception ex) {
            events.add("readError:" + ex.getMessage());
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingItemProcessListener extends AbstractItemProcessListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeProcess(Object item) {
            events.add("beforeProcess:" + item);
        }

        @Override
        public void afterProcess(Object item, Object result) {
            events.add("afterProcess:" + item + "->" + result);
        }

        @Override
        public void onProcessError(Object item, Exception ex) {
            events.add("processError:" + ex.getMessage());
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingItemWriteListener extends AbstractItemWriteListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeWrite(List<Object> items) {
            events.add("beforeWrite:" + items.get(0));
        }

        @Override
        public void afterWrite(List<Object> items) {
            events.add("afterWrite:" + items.get(0));
        }

        @Override
        public void onWriteError(List<Object> items, Exception ex) {
            events.add("writeError:" + ex.getMessage());
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingRetryAndSkipListener implements RetryReadListener, RetryProcessListener,
            RetryWriteListener, SkipReadListener, SkipProcessListener, SkipWriteListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onRetryReadException(Exception ex) {
            events.add("retryRead:" + ex.getMessage());
        }

        @Override
        public void onRetryProcessException(Object item, Exception ex) {
            events.add("retryProcess:" + item + ":" + ex.getMessage());
        }

        @Override
        public void onRetryWriteException(List<Object> items, Exception ex) {
            events.add("retryWrite:" + items.get(0) + ":" + ex.getMessage());
        }

        @Override
        public void onSkipReadItem(Exception ex) {
            events.add("skipRead:" + ex.getMessage());
        }

        @Override
        public void onSkipProcessItem(Object item, Exception ex) {
            events.add("skipProcess:" + item + ":" + ex.getMessage());
        }

        @Override
        public void onSkipWriteItem(List<Object> items, Exception ex) {
            events.add("skipWrite:" + items.get(0) + ":" + ex.getMessage());
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingPartitionAnalyzer extends AbstractPartitionAnalyzer {
        private final List<String> events = new ArrayList<>();

        @Override
        public void analyzeCollectorData(Serializable data) {
            events.add("data:" + data);
        }

        @Override
        public void analyzeStatus(BatchStatus batchStatus, String exitStatus) {
            events.add("status:" + batchStatus + ":" + exitStatus);
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingPartitionReducer extends AbstractPartitionReducer {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beginPartitionedStep() {
            events.add("begin");
        }

        @Override
        public void beforePartitionedStepCompletion() {
            events.add("beforeCompletion");
        }

        @Override
        public void rollbackPartitionedStep() {
            events.add("rollback");
        }

        @Override
        public void afterPartitionedStepCompletion(PartitionStatus status) {
            events.add("after:" + status);
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class SimpleMetric implements Metric {
        private final MetricType type;
        private final long value;

        private SimpleMetric(MetricType type, long value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public MetricType getType() {
            return type;
        }

        @Override
        public long getValue() {
            return value;
        }
    }

    private static final class SimpleJobInstance implements JobInstance {
        private final long instanceId;
        private final String jobName;

        private SimpleJobInstance(long instanceId, String jobName) {
            this.instanceId = instanceId;
            this.jobName = jobName;
        }

        @Override
        public long getInstanceId() {
            return instanceId;
        }

        @Override
        public String getJobName() {
            return jobName;
        }
    }

    private static final class SimpleJobExecution implements JobExecution {
        private final long executionId;
        private final String jobName;
        private final Properties parameters;
        private final Date createTime = new Date();
        private BatchStatus batchStatus;
        private String exitStatus;

        private SimpleJobExecution(long executionId, String jobName, BatchStatus batchStatus, Properties parameters) {
            this.executionId = executionId;
            this.jobName = jobName;
            this.batchStatus = batchStatus;
            this.parameters = parameters;
        }

        @Override
        public long getExecutionId() {
            return executionId;
        }

        @Override
        public String getJobName() {
            return jobName;
        }

        @Override
        public BatchStatus getBatchStatus() {
            return batchStatus;
        }

        @Override
        public Date getStartTime() {
            return createTime;
        }

        @Override
        public Date getEndTime() {
            return batchStatus == BatchStatus.STARTED ? null : createTime;
        }

        @Override
        public String getExitStatus() {
            return exitStatus;
        }

        @Override
        public Date getCreateTime() {
            return createTime;
        }

        @Override
        public Date getLastUpdatedTime() {
            return createTime;
        }

        @Override
        public Properties getJobParameters() {
            return parameters;
        }

        private void setBatchStatus(BatchStatus batchStatus) {
            this.batchStatus = batchStatus;
            this.exitStatus = batchStatus.name();
        }
    }

    private static final class SimpleStepExecution implements StepExecution {
        private final long stepExecutionId;
        private final String stepName;
        private final BatchStatus batchStatus;
        private final String exitStatus;
        private final Serializable persistentUserData;
        private final Metric[] metrics;
        private final Date startTime = new Date();

        private SimpleStepExecution(
                long stepExecutionId,
                String stepName,
                BatchStatus batchStatus,
                String exitStatus,
                Serializable persistentUserData,
                Metric... metrics) {
            this.stepExecutionId = stepExecutionId;
            this.stepName = stepName;
            this.batchStatus = batchStatus;
            this.exitStatus = exitStatus;
            this.persistentUserData = persistentUserData;
            this.metrics = metrics;
        }

        @Override
        public long getStepExecutionId() {
            return stepExecutionId;
        }

        @Override
        public String getStepName() {
            return stepName;
        }

        @Override
        public BatchStatus getBatchStatus() {
            return batchStatus;
        }

        @Override
        public Date getStartTime() {
            return startTime;
        }

        @Override
        public Date getEndTime() {
            return batchStatus == BatchStatus.STARTED ? null : startTime;
        }

        @Override
        public String getExitStatus() {
            return exitStatus;
        }

        @Override
        public Serializable getPersistentUserData() {
            return persistentUserData;
        }

        @Override
        public Metric[] getMetrics() {
            return metrics;
        }
    }

    private static final class SimpleJobContext implements JobContext {
        private final String jobName;
        private final long instanceId;
        private final long executionId;
        private final Properties properties;
        private Object transientUserData;
        private String exitStatus;

        private SimpleJobContext(String jobName, long instanceId, long executionId, Properties properties) {
            this.jobName = jobName;
            this.instanceId = instanceId;
            this.executionId = executionId;
            this.properties = properties;
        }

        @Override
        public String getJobName() {
            return jobName;
        }

        @Override
        public Object getTransientUserData() {
            return transientUserData;
        }

        @Override
        public void setTransientUserData(Object data) {
            transientUserData = data;
        }

        @Override
        public long getInstanceId() {
            return instanceId;
        }

        @Override
        public long getExecutionId() {
            return executionId;
        }

        @Override
        public Properties getProperties() {
            return properties;
        }

        @Override
        public BatchStatus getBatchStatus() {
            return BatchStatus.STARTED;
        }

        @Override
        public String getExitStatus() {
            return exitStatus;
        }

        @Override
        public void setExitStatus(String status) {
            exitStatus = status;
        }
    }

    private static final class SimpleStepContext implements StepContext {
        private final StepExecution stepExecution;
        private final Metric[] metrics;
        private Object transientUserData;
        private Serializable persistentUserData;
        private String exitStatus;

        private SimpleStepContext(StepExecution stepExecution, Metric[] metrics) {
            this.stepExecution = stepExecution;
            this.metrics = metrics;
        }

        @Override
        public String getStepName() {
            return stepExecution.getStepName();
        }

        @Override
        public Object getTransientUserData() {
            return transientUserData;
        }

        @Override
        public void setTransientUserData(Object data) {
            transientUserData = data;
        }

        @Override
        public long getStepExecutionId() {
            return stepExecution.getStepExecutionId();
        }

        @Override
        public Properties getProperties() {
            return new Properties();
        }

        @Override
        public Serializable getPersistentUserData() {
            return persistentUserData;
        }

        @Override
        public void setPersistentUserData(Serializable data) {
            persistentUserData = data;
        }

        @Override
        public BatchStatus getBatchStatus() {
            return stepExecution.getBatchStatus();
        }

        @Override
        public String getExitStatus() {
            return exitStatus;
        }

        @Override
        public void setExitStatus(String status) {
            exitStatus = status;
        }

        @Override
        public Exception getException() {
            return null;
        }

        @Override
        public Metric[] getMetrics() {
            return metrics;
        }
    }

    private static final class InMemoryJobOperator implements JobOperator {
        private final Map<Long, SimpleJobExecution> executions = new LinkedHashMap<>();
        private final Map<Long, SimpleJobInstance> instances = new LinkedHashMap<>();
        private long nextExecutionId = 1L;
        private long nextInstanceId = 1L;

        @Override
        public Set<String> getJobNames() {
            if (instances.isEmpty()) {
                return Collections.emptySet();
            }
            return Set.of(instances.values().iterator().next().getJobName());
        }

        @Override
        public int getJobInstanceCount(String jobName) throws NoSuchJobException {
            requireKnownJob(jobName);
            return (int) instances.values().stream().filter(instance -> instance.getJobName().equals(jobName)).count();
        }

        @Override
        public List<JobInstance> getJobInstances(String jobName, int start, int count) throws NoSuchJobException {
            requireKnownJob(jobName);
            return instances.values().stream()
                    .filter(instance -> instance.getJobName().equals(jobName))
                    .skip(start)
                    .limit(count)
                    .map(JobInstance.class::cast)
                    .toList();
        }

        @Override
        public List<Long> getRunningExecutions(String jobName) throws NoSuchJobException {
            requireKnownJob(jobName);
            return executions.values().stream()
                    .filter(execution -> execution.getJobName().equals(jobName))
                    .filter(execution -> execution.getBatchStatus() == BatchStatus.STARTED)
                    .map(SimpleJobExecution::getExecutionId)
                    .toList();
        }

        @Override
        public Properties getParameters(long executionId) throws NoSuchJobExecutionException {
            return requireExecution(executionId).getJobParameters();
        }

        @Override
        public long start(String jobXmlName, Properties jobParameters) {
            long executionId = nextExecutionId++;
            long instanceId = nextInstanceId++;
            Properties copiedParameters = new Properties();
            copiedParameters.putAll(jobParameters);
            executions.put(executionId, new SimpleJobExecution(executionId, jobXmlName, BatchStatus.STARTED,
                    copiedParameters));
            instances.put(executionId, new SimpleJobInstance(instanceId, jobXmlName));
            return executionId;
        }

        @Override
        public long restart(long executionId, Properties restartParameters) throws NoSuchJobExecutionException {
            SimpleJobExecution execution = requireExecution(executionId);
            Properties copiedParameters = new Properties();
            copiedParameters.putAll(restartParameters);
            return start(execution.getJobName(), copiedParameters);
        }

        @Override
        public void stop(long executionId) throws NoSuchJobExecutionException {
            requireExecution(executionId).setBatchStatus(BatchStatus.STOPPED);
        }

        @Override
        public void abandon(long executionId) throws NoSuchJobExecutionException {
            requireExecution(executionId).setBatchStatus(BatchStatus.ABANDONED);
        }

        @Override
        public JobInstance getJobInstance(long executionId) throws NoSuchJobExecutionException {
            JobInstance instance = instances.get(executionId);
            if (instance == null) {
                throw new NoSuchJobExecutionException("No execution " + executionId);
            }
            return instance;
        }

        @Override
        public List<JobExecution> getJobExecutions(JobInstance instance) throws NoSuchJobInstanceException {
            boolean knownInstance = instances.values().stream()
                    .anyMatch(value -> value.getInstanceId() == instance.getInstanceId());
            if (!knownInstance) {
                throw new NoSuchJobInstanceException("No instance " + instance.getInstanceId());
            }
            return executions.values().stream()
                    .filter(execution -> execution.getJobName().equals(instance.getJobName()))
                    .map(JobExecution.class::cast)
                    .toList();
        }

        @Override
        public JobExecution getJobExecution(long executionId) throws NoSuchJobExecutionException {
            return requireExecution(executionId);
        }

        @Override
        public List<StepExecution> getStepExecutions(long jobExecutionId) throws NoSuchJobExecutionException {
            SimpleJobExecution execution = requireExecution(jobExecutionId);
            Metric readCount = new SimpleMetric(Metric.MetricType.READ_COUNT, 1L);
            return List.of(new SimpleStepExecution(jobExecutionId, execution.getJobName() + ".step",
                    execution.getBatchStatus(), execution.getExitStatus(), "step-state", readCount));
        }

        private void requireKnownJob(String jobName) throws NoSuchJobException {
            boolean knownJob = instances.values().stream().anyMatch(instance -> instance.getJobName().equals(jobName));
            if (!knownJob) {
                throw new NoSuchJobException("No job " + jobName);
            }
        }

        private SimpleJobExecution requireExecution(long executionId) throws NoSuchJobExecutionException {
            return Objects.requireNonNullElseGet(executions.get(executionId), () -> {
                throw new NoSuchJobExecutionException("No execution " + executionId);
            });
        }
    }
}
