/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.extension.resources.HostResource;
import io.opentelemetry.sdk.extension.resources.OsResource;
import io.opentelemetry.sdk.extension.resources.ProcessResource;
import io.opentelemetry.sdk.extension.resources.ProcessRuntimeResource;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.OsTypeValues;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exercises resource detection: the `OsResource`, `HostResource`, `ProcessResource` and
 * `ProcessRuntimeResource` detectors, merged with the SDK default resource and an application
 * resource under the merge, schema URL and attribute-removal rules of `Resource`.
 *
 * A nightly billing report job describes itself the way a deployment does: it detects the
 * operating system, host, process and Java runtime it runs on, layers its own service identity
 * on top so that the application wins every conflict, drops the process command line before it
 * leaves the host, and hands the result to its tracer and meter providers so that every span and
 * metric it exports names the same service, host and runtime.
 */
class NightlyReportResourceTest {

    private static final String DETECTOR_SCHEMA_URL = "https://opentelemetry.io/schemas/1.13.0";

    private static final String LEGACY_SCHEMA_URL = "https://opentelemetry.io/schemas/1.9.0";

    private static final AttributeKey<List<String>> REGIONS = AttributeKey.stringArrayKey("report.regions");

    private static final AttributeKey<Long> SHARD = AttributeKey.longKey("report.shard");

    private static final AttributeKey<Boolean> DRY_RUN = AttributeKey.booleanKey("report.dry_run");

    private static final AttributeKey<String> REPORT = AttributeKey.stringKey("report.name");

    @Test
    void detectorsDescribeTheOperatingSystemHostProcessAndRuntime() throws UnknownHostException {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");

        Resource os = OsResource.get();
        assertThat(os.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
        assertThat(os.getAttribute(ResourceAttributes.OS_TYPE)).isEqualTo(expectedOsType(osName));
        assertThat(os.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isEqualTo(osName + " " + osVersion);

        Resource host = HostResource.get();
        assertThat(host.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
        assertThat(host.getAttribute(ResourceAttributes.HOST_NAME))
                .isEqualTo(InetAddress.getLocalHost().getHostName());
        assertThat(host.getAttribute(ResourceAttributes.HOST_ARCH)).isEqualTo(System.getProperty("os.arch"));

        Resource process = ProcessResource.get();
        assertThat(process.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
        assertThat(process.getAttribute(ResourceAttributes.PROCESS_PID)).isEqualTo(ProcessHandle.current().pid());
        assertThat(process.getAttribute(ResourceAttributes.PROCESS_EXECUTABLE_PATH))
                .isEqualTo(expectedJavaExecutable(System.getProperty("java.home"), osName));

        Resource runtime = ProcessRuntimeResource.get();
        assertThat(runtime.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
        assertThat(runtime.getAttribute(ResourceAttributes.PROCESS_RUNTIME_NAME))
                .isEqualTo(System.getProperty("java.runtime.name"));
        assertThat(runtime.getAttribute(ResourceAttributes.PROCESS_RUNTIME_VERSION))
                .isEqualTo(System.getProperty("java.runtime.version"));
        assertThat(runtime.getAttribute(ResourceAttributes.PROCESS_RUNTIME_DESCRIPTION))
                .isEqualTo(System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.name")
                        + " " + System.getProperty("java.vm.version"));

        assertThat(OsResource.get()).isSameAs(os);
        assertThat(ProcessResource.get()).isSameAs(process);
    }

    @Test
    void applicationIdentityOverridesDefaultsAndDetectedFacts() {
        Resource detected = ReportJob.detectedEnvironment();
        Resource service = ReportJob.serviceResource(detected);

        assertThat(service.getAttribute(ResourceAttributes.SERVICE_NAME)).isEqualTo("nightly-report");
        assertThat(service.getAttribute(ResourceAttributes.SERVICE_NAMESPACE)).isEqualTo("billing");
        assertThat(service.getAttribute(ResourceAttributes.SERVICE_VERSION)).isEqualTo("2024.06.1");
        assertThat(service.getAttribute(ResourceAttributes.SERVICE_INSTANCE_ID)).isEqualTo("report-worker-3");
        assertThat(service.getAttribute(REGIONS)).containsExactly("eu-west-1", "us-east-2");
        assertThat(service.getAttribute(SHARD)).isEqualTo(3L);
        assertThat(service.getAttribute(DRY_RUN)).isFalse();

        assertThat(service.getAttribute(ResourceAttributes.TELEMETRY_SDK_NAME)).isEqualTo("opentelemetry");
        assertThat(service.getAttribute(ResourceAttributes.TELEMETRY_SDK_LANGUAGE)).isEqualTo("java");
        assertThat(service.getAttribute(ResourceAttributes.TELEMETRY_SDK_VERSION)).isEqualTo("1.19.0");

        assertThat(service.getAttribute(ResourceAttributes.OS_TYPE))
                .isEqualTo(OsResource.get().getAttribute(ResourceAttributes.OS_TYPE));
        assertThat(service.getAttribute(ResourceAttributes.HOST_ARCH)).isEqualTo(System.getProperty("os.arch"));
        assertThat(service.getAttribute(ResourceAttributes.PROCESS_PID)).isEqualTo(ProcessHandle.current().pid());
        assertThat(service.getAttribute(ResourceAttributes.PROCESS_RUNTIME_NAME))
                .isEqualTo(System.getProperty("java.runtime.name"));
        assertThat(service.getAttributes().asMap().keySet())
                .extracting(AttributeKey::getKey)
                .doesNotContain("process.command_line", "process.executable.path");
        assertThat(service.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);

        Resource defaults = Resource.getDefault();
        assertThat(defaults.getAttribute(ResourceAttributes.SERVICE_NAME)).isEqualTo("unknown_service:java");
        assertThat(defaults.merge(detected).getAttribute(ResourceAttributes.SERVICE_NAME))
                .isEqualTo("unknown_service:java");
        assertThat(service.merge(Resource.empty())).isSameAs(service);
    }

    @Test
    void schemaUrlSurvivesOnlyWhenTheMergedResourcesAgree() {
        Resource detected = ReportJob.detectedEnvironment();
        assertThat(detected.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);

        Resource unversioned = Resource.create(Attributes.of(REPORT, "invoices"));
        assertThat(unversioned.getSchemaUrl()).isNull();
        assertThat(detected.merge(unversioned).getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
        assertThat(unversioned.merge(detected).getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);

        Resource legacy = Resource.create(Attributes.of(REPORT, "ledger"), LEGACY_SCHEMA_URL);
        Resource conflicting = detected.merge(legacy);
        assertThat(conflicting.getSchemaUrl()).isNull();
        assertThat(conflicting.getAttribute(REPORT)).isEqualTo("ledger");
        assertThat(conflicting.getAttribute(ResourceAttributes.HOST_ARCH)).isEqualTo(System.getProperty("os.arch"));

        Resource rewritten = legacy.toBuilder().setSchemaUrl(DETECTOR_SCHEMA_URL).build();
        assertThat(detected.merge(rewritten).getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
        assertThat(rewritten.getAttribute(REPORT)).isEqualTo("ledger");
    }

    @Test
    void everyExportedSpanAndMetricCarriesTheServiceResource() {
        Resource service = ReportJob.serviceResource(ReportJob.detectedEnvironment());
        InMemorySpanExporter spans = InMemorySpanExporter.create();
        InMemoryMetricReader metrics = InMemoryMetricReader.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(service)
                .addSpanProcessor(SimpleSpanProcessor.create(spans))
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(service)
                .registerMetricReader(metrics)
                .build();
        try {
            ReportJob job = new ReportJob(
                    tracerProvider.tracerBuilder(ReportJob.SCOPE)
                            .setInstrumentationVersion(ReportJob.SCOPE_VERSION)
                            .setSchemaUrl(DETECTOR_SCHEMA_URL)
                            .build(),
                    meterProvider.meterBuilder(ReportJob.SCOPE)
                            .setInstrumentationVersion(ReportJob.SCOPE_VERSION)
                            .setSchemaUrl(DETECTOR_SCHEMA_URL)
                            .build()
                            .counterBuilder("report.rows")
                            .setUnit("{row}")
                            .setDescription("Rows written to nightly reports")
                            .build());
            job.run("invoices", 120);
            job.run("refunds", 7);

            List<SpanData> finished = spans.getFinishedSpanItems();
            assertThat(finished).extracting(SpanData::getName).containsExactly("report invoices", "report refunds");
            for (SpanData span : finished) {
                assertThat(span.getResource()).isEqualTo(service);
                assertThat(span.getResource().getAttribute(ResourceAttributes.HOST_NAME))
                        .isEqualTo(HostResource.get().getAttribute(ResourceAttributes.HOST_NAME));
                assertScope(span.getInstrumentationScopeInfo());
            }

            Collection<MetricData> collected = metrics.collectAllMetrics();
            assertThat(collected).hasSize(1);
            MetricData rows = collected.iterator().next();
            assertThat(rows.getName()).isEqualTo("report.rows");
            assertThat(rows.getUnit()).isEqualTo("{row}");
            assertThat(rows.getResource()).isEqualTo(service);
            assertScope(rows.getInstrumentationScopeInfo());
            assertThat(rows.getLongSumData().getPoints())
                    .extracting(point -> point.getAttributes().get(REPORT), point -> point.getValue())
                    .containsExactlyInAnyOrder(
                            tuple("invoices", 120L),
                            tuple("refunds", 7L));
        } finally {
            CompletableResultCode shutdown = CompletableResultCode.ofAll(
                    List.of(tracerProvider.shutdown(), meterProvider.shutdown())).join(10, TimeUnit.SECONDS);
            assertThat(shutdown.isSuccess()).isTrue();
        }
    }

    private static void assertScope(InstrumentationScopeInfo scope) {
        assertThat(scope.getName()).isEqualTo(ReportJob.SCOPE);
        assertThat(scope.getVersion()).isEqualTo(ReportJob.SCOPE_VERSION);
        assertThat(scope.getSchemaUrl()).isEqualTo(DETECTOR_SCHEMA_URL);
    }

    private static String expectedOsType(String osName) {
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("linux")) {
            return OsTypeValues.LINUX;
        }
        if (lower.startsWith("mac")) {
            return OsTypeValues.DARWIN;
        }
        if (lower.startsWith("windows")) {
            return OsTypeValues.WINDOWS;
        }
        throw new IllegalStateException("Unexpected build host OS: " + osName);
    }

    /** The detector derives the executable from `java.home`, which a process may leave unset. */
    private static String expectedJavaExecutable(String javaHome, String osName) {
        if (javaHome == null) {
            return null;
        }
        String executable = javaHome + File.separatorChar + "bin" + File.separatorChar + "java";
        return osName.toLowerCase(Locale.ROOT).startsWith("windows") ? executable + ".exe" : executable;
    }

    /** The nightly job: builds its resource once and traces and counts every report it writes. */
    private static final class ReportJob {

        static final String SCOPE = "billing.nightly-report";

        static final String SCOPE_VERSION = "2024.06.1";

        private final Tracer tracer;

        private final LongCounter rows;

        ReportJob(Tracer tracer, LongCounter rows) {
            this.tracer = tracer;
            this.rows = rows;
        }

        /** Everything the job can learn about where it runs, without its own identity. */
        static Resource detectedEnvironment() {
            return OsResource.get()
                    .merge(HostResource.get())
                    .merge(ProcessResource.get())
                    .merge(ProcessRuntimeResource.get());
        }

        /**
         * The resource the job exports with: SDK defaults, then detected facts, then its own
         * identity, so each later layer wins; the command line stays on the host.
         */
        static Resource serviceResource(Resource detected) {
            Resource identity = Resource.builder()
                    .put(ResourceAttributes.SERVICE_NAME, "nightly-report")
                    .put(ResourceAttributes.SERVICE_NAMESPACE, "billing")
                    .put(ResourceAttributes.SERVICE_VERSION, "2024.06.1")
                    .put(ResourceAttributes.SERVICE_INSTANCE_ID, "report-worker-3")
                    .put(REGIONS.getKey(), "eu-west-1", "us-east-2")
                    .put(SHARD, 3)
                    .put(DRY_RUN.getKey(), false)
                    .build();
            return Resource.getDefault()
                    .merge(detected)
                    .merge(identity)
                    .toBuilder()
                    .removeIf(key -> key.equals(ResourceAttributes.PROCESS_COMMAND_LINE)
                            || key.equals(ResourceAttributes.PROCESS_EXECUTABLE_PATH))
                    .build();
        }

        void run(String report, long rowCount) {
            Span span = tracer.spanBuilder("report " + report).setAttribute(REPORT, report).startSpan();
            try {
                rows.add(rowCount, Attributes.of(REPORT, report));
            } finally {
                span.end();
            }
        }
    }
}
