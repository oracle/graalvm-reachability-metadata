/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_proto.opentelemetry_proto;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.MetricsData;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.proto.trace.v1.TracesData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Opentelemetry_protoTest {
    private static final ByteString TRACE_ID = ByteString.copyFromUtf8("0123456789abcdef");
    private static final ByteString SPAN_ID = ByteString.copyFromUtf8("01234567");

    @Test
    void traceExportRequestRetainsResourceScopeAndSpanDetailsAfterSerialization() throws Exception {
        Span span = Span.newBuilder()
                .setTraceId(TRACE_ID)
                .setSpanId(SPAN_ID)
                .setParentSpanId(ByteString.copyFromUtf8("parent123"))
                .setTraceState("vendor=value")
                .setFlags(1)
                .setName("GET /orders/{id}")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .setStartTimeUnixNano(100L)
                .setEndTimeUnixNano(250L)
                .addAttributes(attribute("http.request.method", "GET"))
                .addEvents(Span.Event.newBuilder()
                        .setTimeUnixNano(150L)
                        .setName("validated")
                        .addAttributes(attribute("validation.result", "accepted")))
                .addLinks(Span.Link.newBuilder()
                        .setTraceId(ByteString.copyFromUtf8("fedcba9876543210"))
                        .setSpanId(ByteString.copyFromUtf8("76543210"))
                        .setFlags(1)
                        .addAttributes(attribute("link.type", "causal")))
                .setStatus(Status.newBuilder()
                        .setCode(Status.StatusCode.STATUS_CODE_ERROR)
                        .setMessage("upstream unavailable"))
                .build();
        ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource())
                .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
                .addScopeSpans(ScopeSpans.newBuilder()
                        .setScope(scope())
                        .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
                        .addSpans(span))
                .build();
        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(resourceSpans)
                .build();

        ExportTraceServiceRequest parsed = ExportTraceServiceRequest.parseFrom(request.toByteArray());
        Span parsedSpan = parsed.getResourceSpans(0).getScopeSpans(0).getSpans(0);

        assertThat(parsed).isEqualTo(request);
        assertThat(TracesData.parseFrom(TracesData.newBuilder().addResourceSpans(resourceSpans).build()
                .toByteArray()).getResourceSpansCount()).isEqualTo(1);
        assertThat(parsedSpan.getKind()).isEqualTo(Span.SpanKind.SPAN_KIND_SERVER);
        assertThat(parsedSpan.getEvents(0).getAttributes(0).getValue().getStringValue()).isEqualTo("accepted");
        assertThat(parsedSpan.getLinks(0).getTraceId()).isEqualTo(ByteString.copyFromUtf8("fedcba9876543210"));
        assertThat(parsedSpan.getStatus().getCode()).isEqualTo(Status.StatusCode.STATUS_CODE_ERROR);
    }

    @Test
    void metricsExportSupportsGaugeAndCumulativeSumDataPoints() throws Exception {
        NumberDataPoint gaugePoint = NumberDataPoint.newBuilder()
                .setStartTimeUnixNano(10L)
                .setTimeUnixNano(20L)
                .setAsDouble(23.5D)
                .addAttributes(attribute("room", "kitchen"))
                .build();
        NumberDataPoint sumPoint = NumberDataPoint.newBuilder()
                .setStartTimeUnixNano(10L)
                .setTimeUnixNano(20L)
                .setAsInt(42L)
                .addAttributes(attribute("operation", "checkout"))
                .build();
        ResourceMetrics resourceMetrics = ResourceMetrics.newBuilder()
                .setResource(resource())
                .addScopeMetrics(ScopeMetrics.newBuilder()
                        .setScope(scope())
                        .addMetrics(Metric.newBuilder()
                                .setName("temperature")
                                .setDescription("Current temperature")
                                .setUnit("Cel")
                                .setGauge(Gauge.newBuilder().addDataPoints(gaugePoint)))
                        .addMetrics(Metric.newBuilder()
                                .setName("requests")
                                .setUnit("1")
                                .setSum(Sum.newBuilder()
                                        .setAggregationTemporality(
                                                AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                                        .setIsMonotonic(true)
                                        .addDataPoints(sumPoint))))
                .build();
        ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(resourceMetrics)
                .build();

        ExportMetricsServiceRequest parsed = ExportMetricsServiceRequest.parseFrom(request.toByteArray());
        Metric parsedGauge = parsed.getResourceMetrics(0).getScopeMetrics(0).getMetrics(0);
        Metric parsedSum = parsed.getResourceMetrics(0).getScopeMetrics(0).getMetrics(1);

        assertThat(parsed).isEqualTo(request);
        assertThat(MetricsData.parseFrom(MetricsData.newBuilder().addResourceMetrics(resourceMetrics).build()
                .toByteArray()).getResourceMetricsCount()).isEqualTo(1);
        assertThat(parsedGauge.getDataCase()).isEqualTo(Metric.DataCase.GAUGE);
        assertThat(parsedGauge.getGauge().getDataPoints(0).getAsDouble()).isEqualTo(23.5D);
        assertThat(parsedSum.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(parsedSum.getSum().getDataPoints(0).getAsInt()).isEqualTo(42L);
    }

    @Test
    void logsExportPreservesSeverityBodyCorrelationAndResourceContext() throws Exception {
        LogRecord record = LogRecord.newBuilder()
                .setTimeUnixNano(500L)
                .setObservedTimeUnixNano(510L)
                .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_WARN)
                .setSeverityText("WARN")
                .setBody(AnyValue.newBuilder().setStringValue("payment authorization delayed"))
                .addAttributes(attribute("order.id", "1234"))
                .setTraceId(TRACE_ID)
                .setSpanId(SPAN_ID)
                .setFlags(1)
                .setEventName("payment.authorization")
                .build();
        ResourceLogs resourceLogs = ResourceLogs.newBuilder()
                .setResource(resource())
                .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
                .addScopeLogs(ScopeLogs.newBuilder()
                        .setScope(scope())
                        .addLogRecords(record))
                .build();
        ExportLogsServiceRequest request = ExportLogsServiceRequest.newBuilder()
                .addResourceLogs(resourceLogs)
                .build();

        ExportLogsServiceRequest parsed = ExportLogsServiceRequest.parseFrom(request.toByteArray());
        LogRecord parsedRecord = parsed.getResourceLogs(0).getScopeLogs(0).getLogRecords(0);

        assertThat(parsed).isEqualTo(request);
        assertThat(parsedRecord.getSeverityNumber()).isEqualTo(SeverityNumber.SEVERITY_NUMBER_WARN);
        assertThat(parsedRecord.getBody().getStringValue()).isEqualTo("payment authorization delayed");
        assertThat(parsedRecord.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(parsedRecord.getEventName()).isEqualTo("payment.authorization");
    }

    private static Resource resource() {
        return Resource.newBuilder()
                .addAttributes(attribute("service.name", "checkout"))
                .addAttributes(attribute("service.instance.id", "instance-1"))
                .build();
    }

    private static InstrumentationScope scope() {
        return InstrumentationScope.newBuilder()
                .setName("checkout-instrumentation")
                .setVersion("1.0")
                .addAttributes(attribute("instrumentation.language", "java"))
                .build();
    }

    private static KeyValue attribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }
}
