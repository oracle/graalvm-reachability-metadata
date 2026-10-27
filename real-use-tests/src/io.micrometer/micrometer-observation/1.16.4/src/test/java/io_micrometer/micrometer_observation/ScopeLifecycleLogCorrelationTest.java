/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manages the observation scope stack on a thread: nested scopes, `scoped` and `tryScoped`
 * blocks, and `Scope.reset` and `Scope.makeCurrent` hand-offs, with scope callbacks fanned out
 * by an all-matching composite handler.
 *
 * A batch scheduler runs jobs made of steps. A log-correlation handler keeps the application's
 * own MDC-style map in sync with the current scope, storing what it must restore in the
 * observation context, and an audit handler only follows job observations. When a job is
 * suspended the thread is reset so it can serve other work, and when the job resumes its scope
 * is made current again. The test asserts on the correlation map, the registry's current
 * observation, and the audit trail an operator would read.
 */
class ScopeLifecycleLogCorrelationTest {

    private ObservationRegistry registry;

    private AuditHandler audit;

    private BatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        audit = new AuditHandler();
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(
                new ObservationHandler.AllMatchingCompositeObservationHandler(new LogCorrelationHandler(), audit));
        scheduler = new BatchScheduler(registry);
    }

    @AfterEach
    void tearDown() {
        LogCorrelation.clear();
    }

    @Test
    void nestedScopesKeepCorrelationInSyncWithTheCurrentObservation() {
        List<String> logLines = scheduler.runJob("nightly-import", List.of("download", "parse"));

        assertThat(logLines).containsExactly(
                "[job=nightly-import] job started",
                "[job=nightly-import step=download] running download",
                "[job=nightly-import] after download",
                "[job=nightly-import step=parse] running parse",
                "[job=nightly-import] after parse");
        assertThat(LogCorrelation.snapshot()).isEmpty();
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(registry.getCurrentObservationScope()).isNull();
        assertThat(audit.trail).containsExactly(
                "start:nightly-import",
                "scope-open:nightly-import",
                "scope-close:nightly-import",
                "stop:nightly-import scopes=1");
    }

    @Test
    void stepScopeLinksBackToTheJobScopeAndParentObservation() {
        Observation job = Observation.createNotStarted("batch.job", () -> new JobContext("reindex"), registry)
                .contextualName("reindex")
                .start();
        try (Observation.Scope jobScope = job.openScope()) {
            Observation step = Observation.createNotStarted("batch.step", registry)
                    .contextualName("shard-1")
                    .start();
            assertThat(step.getContext().getParentObservation()).isSameAs(job);

            try (Observation.Scope stepScope = step.openScope()) {
                assertThat(registry.getCurrentObservationScope()).isSameAs(stepScope);
                assertThat(stepScope.getCurrentObservation()).isSameAs(step);
                assertThat(stepScope.getPreviousObservationScope()).isSameAs(jobScope);
                assertThat(stepScope.isNoop()).isFalse();
                assertThat(LogCorrelation.render()).isEqualTo("[job=reindex step=shard-1]");
            }
            step.stop();

            assertThat(registry.getCurrentObservationScope()).isSameAs(jobScope);
            assertThat(LogCorrelation.render()).isEqualTo("[job=reindex]");
        } finally {
            job.stop();
        }
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void reopeningTheSameObservationNestsScopesAndCountsThemInTheContext() {
        Observation job = Observation.createNotStarted("batch.job", () -> new JobContext("compact"), registry)
                .contextualName("compact")
                .start();
        try (Observation.Scope outer = job.openScope()) {
            try (Observation.Scope inner = job.openScope()) {
                assertThat(inner.getPreviousObservationScope()).isSameAs(outer);
                assertThat(registry.getCurrentObservation()).isSameAs(job);
            }
            assertThat(registry.getCurrentObservationScope()).isSameAs(outer);
            assertThat(LogCorrelation.render()).isEqualTo("[job=compact]");
        }
        job.stop();

        assertThat(LogCorrelation.snapshot()).isEmpty();
        assertThat(audit.trail).containsExactly(
                "start:compact",
                "scope-open:compact",
                "scope-open:compact",
                "scope-close:compact",
                "scope-close:compact",
                "stop:compact scopes=2");
    }

    @Test
    void suspendedJobResetsTheThreadAndResumesByMakingItsScopeCurrent() {
        Observation job = Observation.createNotStarted("batch.job", () -> new JobContext("export"), registry)
                .contextualName("export")
                .start();
        Observation.Scope jobScope = job.openScope();
        Observation step = Observation.createNotStarted("batch.step", registry).contextualName("upload").start();
        Observation.Scope stepScope = step.openScope();
        assertThat(LogCorrelation.render()).isEqualTo("[job=export step=upload]");

        stepScope.reset();

        assertThat(registry.getCurrentObservationScope()).isNull();
        assertThat(LogCorrelation.snapshot()).isEmpty();

        String unrelated = Observation.tryScoped(registry.getCurrentObservation(),
                () -> "unrelated work " + LogCorrelation.render());
        assertThat(unrelated).isEqualTo("unrelated work []");

        stepScope.makeCurrent();

        assertThat(registry.getCurrentObservationScope()).isSameAs(stepScope);
        assertThat(registry.getCurrentObservation()).isSameAs(step);
        assertThat(LogCorrelation.render()).isEqualTo("[job=export step=upload]");
        assertThat(audit.trail).containsSubsequence("scope-reset:export", "scope-open:export");

        stepScope.close();
        step.stop();
        assertThat(registry.getCurrentObservationScope()).isSameAs(jobScope);
        assertThat(LogCorrelation.render()).isEqualTo("[job=export]");

        jobScope.close();
        job.stop();
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(LogCorrelation.snapshot()).isEmpty();
        assertThat(audit.trail).last().isEqualTo("stop:export scopes=2");
    }

    @Test
    void tryScopedRunsInTheGivenObservationOrWithoutAnyScope() {
        Observation job = Observation.createNotStarted("batch.job", () -> new JobContext("purge"), registry)
                .contextualName("purge")
                .start();

        String scoped = Observation.tryScoped(job, () -> LogCorrelation.render());
        String unscoped = Observation.tryScoped(null, () -> LogCorrelation.render());
        List<String> runnableLog = new ArrayList<>();
        Observation.tryScoped(job, () -> {
            runnableLog.add(String.valueOf(registry.getCurrentObservation() == job));
        });
        job.stop();

        assertThat(scoped).isEqualTo("[job=purge]");
        assertThat(unscoped).isEqualTo("[]");
        assertThat(runnableLog).containsExactly("true");
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(audit.trail).last().isEqualTo("stop:purge scopes=2");
    }

    /** Application's own MDC-style correlation map, kept per thread. */
    static final class LogCorrelation {

        private static final ThreadLocal<Map<String, String>> FIELDS = ThreadLocal.withInitial(HashMap::new);

        private LogCorrelation() {
        }

        static Map<String, String> snapshot() {
            return new HashMap<>(FIELDS.get());
        }

        static void replace(Map<String, String> fields) {
            FIELDS.set(new HashMap<>(fields));
        }

        static void clear() {
            FIELDS.get().clear();
        }

        static String render() {
            Map<String, String> fields = FIELDS.get();
            List<String> parts = new ArrayList<>();
            if (fields.containsKey("job")) {
                parts.add("job=" + fields.get("job"));
            }
            if (fields.containsKey("step")) {
                parts.add("step=" + fields.get("step"));
            }
            return "[" + String.join(" ", parts) + "]";
        }
    }

    /** Context of a whole batch job; the audit handler only follows these. */
    static final class JobContext extends Observation.Context {

        private final String jobName;

        JobContext(String jobName) {
            this.jobName = jobName;
        }

        String jobName() {
            return jobName;
        }
    }

    /** Keeps the correlation map in sync with the scope stack, saving state in the context. */
    static final class LogCorrelationHandler implements ObservationHandler<Observation.Context> {

        private static final String SAVED_FIELDS = "log-correlation.saved";

        @Override
        public void onScopeOpened(Observation.Context context) {
            Map<String, String> previous = LogCorrelation.snapshot();
            List<Map<String, String>> saved = context.computeIfAbsent(SAVED_FIELDS, key -> new ArrayList<>());
            saved.add(previous);
            Map<String, String> fields = new HashMap<>(previous);
            fields.put(context instanceof JobContext ? "job" : "step", context.getContextualName());
            LogCorrelation.replace(fields);
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            List<Map<String, String>> saved = context.getRequired(SAVED_FIELDS);
            LogCorrelation.replace(saved.remove(saved.size() - 1));
        }

        @Override
        public void onScopeReset(Observation.Context context) {
            context.remove(SAVED_FIELDS);
            LogCorrelation.clear();
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    /** Records the lifecycle of job observations, counting scope openings in the context. */
    static final class AuditHandler implements ObservationHandler<JobContext> {

        private static final String SCOPE_COUNT = "audit.scope-count";

        final List<String> trail = new ArrayList<>();

        @Override
        public void onStart(JobContext context) {
            trail.add("start:" + context.jobName());
        }

        @Override
        public void onScopeOpened(JobContext context) {
            context.computeIfAbsent(SCOPE_COUNT, key -> new AtomicInteger()).incrementAndGet();
            trail.add("scope-open:" + context.jobName());
        }

        @Override
        public void onScopeClosed(JobContext context) {
            trail.add("scope-close:" + context.jobName());
        }

        @Override
        public void onScopeReset(JobContext context) {
            trail.add("scope-reset:" + context.jobName());
        }

        @Override
        public void onStop(JobContext context) {
            AtomicInteger scopes = context.getOrDefault(SCOPE_COUNT, new AtomicInteger());
            trail.add("stop:" + context.jobName() + " scopes=" + scopes.get());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof JobContext;
        }
    }

    /** Runs a job and its steps, writing log lines prefixed with the correlation fields. */
    static final class BatchScheduler {

        private final ObservationRegistry registry;

        BatchScheduler(ObservationRegistry registry) {
            this.registry = registry;
        }

        List<String> runJob(String jobName, List<String> steps) {
            List<String> logLines = new ArrayList<>();
            Observation job = Observation.createNotStarted("batch.job", () -> new JobContext(jobName), registry)
                    .contextualName(jobName);
            job.observe(() -> {
                logLines.add(LogCorrelation.render() + " job started");
                for (String stepName : steps) {
                    Observation step = Observation.createNotStarted("batch.step", registry)
                            .contextualName(stepName)
                            .start();
                    try {
                        logLines.add(step.scoped(() -> LogCorrelation.render() + " running " + stepName));
                    } finally {
                        step.stop();
                    }
                    logLines.add(LogCorrelation.render() + " after " + stepName);
                }
            });
            return logLines;
        }
    }
}
