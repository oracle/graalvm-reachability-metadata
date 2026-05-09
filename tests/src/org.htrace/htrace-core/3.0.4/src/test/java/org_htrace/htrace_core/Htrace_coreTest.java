/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_htrace.htrace_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.htrace.HTraceConfiguration;
import org.htrace.Sampler;
import org.htrace.Span;
import org.htrace.TimelineAnnotation;
import org.htrace.Trace;
import org.htrace.TraceInfo;
import org.htrace.TraceScope;
import org.htrace.TraceTree;
import org.htrace.impl.CountSampler;
import org.htrace.impl.LocalFileSpanReceiver;
import org.htrace.impl.MilliSpan;
import org.htrace.impl.POJOSpanReceiver;
import org.htrace.impl.ProbabilitySampler;
import org.htrace.impl.TrueIfTracingSampler;
import org.htrace.wrappers.TraceExecutorService;
import org.htrace.wrappers.TraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Htrace_coreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void configurationParsesDefaultsAndSamplersMakeTracingDecisions() {
        Map<String, String> values = new HashMap<>();
        values.put("enabled", " true ");
        values.put("disabled", "FALSE");
        values.put("workers", "7");
        values.put("blank", "  ");
        values.put("bad-int", "many");
        HTraceConfiguration configuration = HTraceConfiguration.fromMap(values);
        values.put("workers", "9");

        assertThat(configuration.get("workers")).isEqualTo("7");
        assertThat(configuration.get("missing", "fallback")).isEqualTo("fallback");
        assertThat(configuration.getBoolean("enabled", false)).isTrue();
        assertThat(configuration.getBoolean("disabled", true)).isFalse();
        assertThat(configuration.getBoolean("not-a-boolean", true)).isTrue();
        assertThat(configuration.getInt("workers", 1)).isEqualTo(7);
        assertThat(configuration.getInt("blank", 3)).isEqualTo(3);
        assertThatThrownBy(() -> configuration.getInt("bad-int", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad-int");

        assertThat(Sampler.ALWAYS.next(null)).isTrue();
        assertThat(Sampler.NEVER.next(null)).isFalse();
        assertThat(new ProbabilitySampler(1.0).next(null)).isTrue();
        assertThat(new ProbabilitySampler(0.0).next(null)).isFalse();
        assertThat(new CountSampler(1).next(null)).isTrue();
        assertThat(TrueIfTracingSampler.INSTANCE.next(null)).isFalse();
    }

    @Test
    void nestedScopesDeliverAnnotatedSpansAndBuildTraceTree() throws Exception {
        POJOSpanReceiver receiver = new POJOSpanReceiver();
        Trace.setProcessId("process-nested-scopes");
        Trace.addReceiver(receiver);
        byte[] key = "component".getBytes(StandardCharsets.UTF_8);
        byte[] value = "storage".getBytes(StandardCharsets.UTF_8);

        try {
            try (TraceScope rootScope = Trace.startSpan("root-operation", Sampler.ALWAYS)) {
                Span root = rootScope.getSpan();
                assertThat(Trace.isTracing()).isTrue();
                assertThat(Trace.currentSpan()).isSameAs(root);
                assertThat(root.getDescription()).isEqualTo("root-operation");
                assertThat(root.getParentId()).isEqualTo(Span.ROOT_SPAN_ID);

                Trace.addKVAnnotation(key, value);
                Trace.addTimelineAnnotation("root-started");

                try (TraceScope childScope = Trace.startSpan("child-operation")) {
                    Span child = childScope.getSpan();
                    assertThat(child.getTraceId()).isEqualTo(root.getTraceId());
                    assertThat(child.getParentId()).isEqualTo(root.getSpanId());
                    assertThat(Trace.currentSpan()).isSameAs(child);
                    Trace.addTimelineAnnotation("child-finished");
                }

                assertThat(Trace.currentSpan()).isSameAs(root);
            }

            assertThat(Trace.isTracing()).isFalse();
            assertThat(receiver.getSpans()).hasSize(2);
            Span root = singleSpanWithDescription(receiver.getSpans(), "root-operation");
            Span child = singleSpanWithDescription(receiver.getSpans(), "child-operation");

            assertThat(root.isRunning()).isFalse();
            assertThat(root.getStopTimeMillis()).isGreaterThanOrEqualTo(root.getStartTimeMillis());
            assertThat(root.getKVAnnotations()).containsEntry(key, value);
            assertThat(root.getTimelineAnnotations())
                    .extracting(TimelineAnnotation::getMessage)
                    .containsExactly("root-started");
            assertThat(child.getTimelineAnnotations())
                    .extracting(TimelineAnnotation::getMessage)
                    .containsExactly("child-finished");

            TraceTree tree = new TraceTree(receiver.getSpans());
            assertThat(tree.getSpans()).containsExactlyInAnyOrder(root, child);
            assertThat(tree.getRoots()).containsExactly(root);
            assertThat(tree.getSpansByParentIdMap().get(root.getSpanId())).containsExactly(child);
            assertThat(tree.getSpansByPidMap().get("process-nested-scopes")).containsExactlyInAnyOrder(root, child);
        } finally {
            Trace.removeReceiver(receiver);
            receiver.close();
        }
    }

    @Test
    void traceInfoCanStartDetachedSpanAndContinueItLater() throws Exception {
        POJOSpanReceiver receiver = new POJOSpanReceiver();
        Trace.setProcessId("process-detached-scope");
        Trace.addReceiver(receiver);

        try {
            TraceInfo parentInfo = new TraceInfo(101L, 202L);
            TraceScope scope = Trace.startSpan("continued-from-info", parentInfo);
            Span span = scope.detach();

            assertThat(scope.isDetached()).isTrue();
            assertThat(Trace.isTracing()).isFalse();
            assertThat(receiver.getSpans()).isEmpty();
            assertThat(span.getTraceId()).isEqualTo(parentInfo.traceId);
            assertThat(span.getParentId()).isEqualTo(parentInfo.spanId);
            assertThat(TraceInfo.fromSpan(span).traceId).isEqualTo(parentInfo.traceId);
            assertThat(TraceInfo.fromSpan(null)).isNull();

            try (TraceScope continued = Trace.continueSpan(span)) {
                assertThat(Trace.currentSpan()).isSameAs(span);
                assertThat(continued.getSpan()).isSameAs(span);
            }

            scope.close();
            assertThat(receiver.getSpans()).containsExactly(span);
            assertThat(span.isRunning()).isFalse();
        } finally {
            Trace.removeReceiver(receiver);
            receiver.close();
        }
    }

    @Test
    void callableRunnableAndExecutorWrappersPropagateTheCurrentSpan() throws Exception {
        POJOSpanReceiver receiver = new POJOSpanReceiver();
        ExecutorService backingExecutor = Executors.newSingleThreadExecutor();
        TraceExecutorService traceExecutor = new TraceExecutorService(backingExecutor);
        Trace.setProcessId("process-wrapper-scope");
        Trace.addReceiver(receiver);

        try {
            try (TraceScope rootScope = Trace.startSpan("wrapper-root", Sampler.ALWAYS)) {
                Span root = rootScope.getSpan();

                Callable<String> callable = Trace.wrap(() -> Trace.currentSpan().getDescription());
                assertThat(callable.call()).isEqualTo(Thread.currentThread().getName());

                AtomicReference<String> runnableSpan = new AtomicReference<>();
                Trace.wrap("named-runnable", () -> runnableSpan.set(Trace.currentSpan().getDescription())).run();
                assertThat(runnableSpan).hasValue("named-runnable");

                Future<String> submitted = traceExecutor.submit(() -> {
                    Span currentSpan = Trace.currentSpan();
                    assertThat(currentSpan.getTraceId()).isEqualTo(root.getTraceId());
                    assertThat(currentSpan.getParentId()).isEqualTo(root.getSpanId());
                    return currentSpan.getDescription();
                });
                assertThat(submitted.get(5, TimeUnit.SECONDS)).isNotEmpty();
            }
        } finally {
            traceExecutor.shutdown();
            if (!traceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                traceExecutor.shutdownNow();
            }
            Trace.removeReceiver(receiver);
            receiver.close();
        }

        assertThat(receiver.getSpans())
                .extracting(Span::getDescription)
                .contains("wrapper-root", Thread.currentThread().getName(), "named-runnable");
        assertThat(receiver.getSpans()).hasSize(4);
    }

    @Test
    void localFileReceiverWritesSpanJson() throws Exception {
        Path output = temporaryDirectory.resolve("spans.json");
        Map<String, String> configuration = new HashMap<>();
        configuration.put(LocalFileSpanReceiver.PATH_KEY, output.toString());
        configuration.put(LocalFileSpanReceiver.CAPACITY_KEY, "2");
        LocalFileSpanReceiver receiver = new LocalFileSpanReceiver();
        receiver.configure(HTraceConfiguration.fromMap(configuration));

        Span span = new MilliSpan("file-receiver-span", 11L, Span.ROOT_SPAN_ID, 22L, "process-file");
        span.addTimelineAnnotation("written-to-file");
        receiver.receiveSpan(span);
        receiver.close();

        String fileContents = Files.readString(output);
        assertThat(fileContents).contains("TraceID", "SpanID", "ParentID", "ProcessID", "Description");
        assertThat(fileContents).contains("file-receiver-span", "process-file", "written-to-file");
    }

    @Test
    void traceProxyCreatesSpansAroundInterfaceMethodCalls() throws Exception {
        POJOSpanReceiver receiver = new POJOSpanReceiver();
        Trace.setProcessId("process-proxy-scope");
        Trace.addReceiver(receiver);

        try {
            GreetingService traced = TraceProxy.trace(new GreetingServiceImpl());

            assertThat(traced.greet("HTrace")).isEqualTo("Hello, HTrace");
            assertThat(traced.sum(2, 5)).isEqualTo(7);

            assertThat(receiver.getSpans())
                    .extracting(Span::getDescription)
                    .containsExactlyInAnyOrder("greet", "sum");

            GreetingService untraced = TraceProxy.trace(new GreetingServiceImpl(), Sampler.NEVER);
            assertThat(untraced.greet("Sampler")).isEqualTo("Hello, Sampler");
            assertThat(receiver.getSpans()).hasSize(2);
        } finally {
            Trace.removeReceiver(receiver);
            receiver.close();
        }
    }

    @Test
    void nullScopeAndStoppedMilliSpanExposeStableValues() {
        TraceScope nullScope = Trace.continueSpan(null);
        nullScope.close();

        assertThat(nullScope.getSpan()).isNull();
        assertThat(nullScope.isDetached()).isFalse();
        assertThat(nullScope.toString()).isEqualTo("NullScope");
        assertThat(Trace.startSpan("not-started-by-never-sampler", Sampler.NEVER).getSpan()).isNull();

        MilliSpan span = new MilliSpan("manual-span", 33L, Span.ROOT_SPAN_ID, 44L, "manual-process");
        assertThat(span.isRunning()).isTrue();
        assertThat(span.toString()).contains("manual-span");

        Span child = span.child("manual-child");
        assertThat(child.getTraceId()).isEqualTo(span.getTraceId());
        assertThat(child.getParentId()).isEqualTo(span.getSpanId());
        assertThat(child.getProcessId()).isEqualTo(span.getProcessId());

        span.stop();
        assertThat(span.isRunning()).isFalse();
        assertThat(span.getAccumulatedMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(span.getStopTimeMillis()).isGreaterThanOrEqualTo(span.getStartTimeMillis());
    }

    private static Span singleSpanWithDescription(Collection<Span> spans, String description) {
        List<Span> matches = spans.stream()
                .filter(span -> description.equals(span.getDescription()))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    public interface GreetingService {
        String greet(String name);

        int sum(int left, int right);
    }

    private static final class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }

        @Override
        public int sum(int left, int right) {
            return left + right;
        }
    }
}
