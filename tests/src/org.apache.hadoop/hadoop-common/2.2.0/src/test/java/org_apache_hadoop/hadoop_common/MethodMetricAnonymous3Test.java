/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.metrics2.AbstractMetric;
import org.apache.hadoop.metrics2.MetricsCollector;
import org.apache.hadoop.metrics2.MetricsInfo;
import org.apache.hadoop.metrics2.MetricsRecordBuilder;
import org.apache.hadoop.metrics2.MetricsSource;
import org.apache.hadoop.metrics2.MetricsTag;
import org.apache.hadoop.metrics2.annotation.Metric;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.MetricsAnnotations;
import org.junit.jupiter.api.Test;

public class MethodMetricAnonymous3Test {
    @Test
    void annotatedTagMethodIsInvokedWhenMetricsSourceIsSnapshotted() {
        AnnotatedTagSource annotatedSource = new AnnotatedTagSource();
        MetricsSource metricsSource = MetricsAnnotations.makeSource(annotatedSource);
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        metricsSource.getMetrics(collector, true);

        assertThat(annotatedSource.invocations).isEqualTo(1);
        assertThat(collector.records).hasSize(1);
        assertThat(collector.records.get(0).tags).containsEntry("ClusterId", "cluster-alpha");
    }

    @Metrics(context = "method-metric-tag-test")
    private static class AnnotatedTagSource {
        private int invocations;

        @Metric(
                value = { "ClusterId", "Cluster identifier" },
                type = Metric.Type.TAG)
        public String getClusterId() {
            invocations++;
            return "cluster-alpha";
        }
    }

    private static final class RecordingMetricsCollector implements MetricsCollector {
        private final List<RecordingMetricsRecordBuilder> records = new ArrayList<>();

        @Override
        public MetricsRecordBuilder addRecord(String name) {
            return addRecord(new SimpleMetricsInfo(name, name + " record"));
        }

        @Override
        public MetricsRecordBuilder addRecord(MetricsInfo info) {
            RecordingMetricsRecordBuilder record = new RecordingMetricsRecordBuilder(this);
            records.add(record);
            return record;
        }
    }

    private static final class RecordingMetricsRecordBuilder extends MetricsRecordBuilder {
        private final RecordingMetricsCollector parent;
        private final Map<String, String> tags = new LinkedHashMap<>();

        private RecordingMetricsRecordBuilder(RecordingMetricsCollector parent) {
            this.parent = parent;
        }

        @Override
        public MetricsRecordBuilder tag(MetricsInfo tagInfo, String value) {
            tags.put(tagInfo.name(), value);
            return this;
        }

        @Override
        public MetricsRecordBuilder add(MetricsTag tag) {
            tags.put(tag.name(), tag.value());
            return this;
        }

        @Override
        public MetricsRecordBuilder add(AbstractMetric metric) {
            return this;
        }

        @Override
        public MetricsRecordBuilder setContext(String value) {
            tags.put("Context", value);
            return this;
        }

        @Override
        public MetricsRecordBuilder addCounter(MetricsInfo counterInfo, int value) {
            return this;
        }

        @Override
        public MetricsRecordBuilder addCounter(MetricsInfo counterInfo, long value) {
            return this;
        }

        @Override
        public MetricsRecordBuilder addGauge(MetricsInfo gaugeInfo, int value) {
            return this;
        }

        @Override
        public MetricsRecordBuilder addGauge(MetricsInfo gaugeInfo, long value) {
            return this;
        }

        @Override
        public MetricsRecordBuilder addGauge(MetricsInfo gaugeInfo, float value) {
            return this;
        }

        @Override
        public MetricsRecordBuilder addGauge(MetricsInfo gaugeInfo, double value) {
            return this;
        }

        @Override
        public MetricsCollector parent() {
            return parent;
        }
    }

    private static final class SimpleMetricsInfo implements MetricsInfo {
        private final String name;
        private final String description;

        private SimpleMetricsInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }
    }
}
