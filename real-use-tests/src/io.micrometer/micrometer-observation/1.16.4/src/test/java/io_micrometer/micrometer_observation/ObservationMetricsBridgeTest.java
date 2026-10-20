/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Turns observations into metrics through a meter observation handler registered on the
 * observation registry.
 *
 * A report-export job instruments its work once with the Observation API; the registry hands each
 * observation to `DefaultMeterObservationHandler`, which records timers tagged with the low
 * cardinality key values and the error, long-task timers for in-flight work, and counters for
 * events. The test asserts on the meters an operator would see in the meter registry.
 */
class ObservationMetricsBridgeTest {

    private SimpleMeterRegistry meterRegistry;

    private ObservationRegistry observationRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    }

    @Test
    void completedExportsAreTimedPerFormatWithoutHighCardinalityTags() {
        ReportExporter exporter = new ReportExporter(observationRegistry, meterRegistry);

        assertThat(exporter.export("r-1", "csv", 120)).isEqualTo("r-1.csv");
        assertThat(exporter.export("r-2", "csv", 80)).isEqualTo("r-2.csv");
        assertThat(exporter.export("r-3", "pdf", 5)).isEqualTo("r-3.pdf");

        Timer csv = meterRegistry.get("reports.export").tags("format", "csv", "error", "none").timer();
        Timer pdf = meterRegistry.get("reports.export").tags("format", "pdf", "error", "none").timer();
        assertThat(csv.count()).isEqualTo(2);
        assertThat(pdf.count()).isEqualTo(1);
        assertThat(csv.totalTime(TimeUnit.NANOSECONDS)).isPositive();
        assertThat(csv.getId().getTags()).extracting(Tag::getKey).doesNotContain("report.id");

        Counter rows = meterRegistry.get("reports.export.rows.written").tags("format", "csv").counter();
        assertThat(rows.count()).isEqualTo(2.0);
        assertThat(meterRegistry.get("reports.export.rows.written").tags("format", "pdf").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void inFlightExportIsVisibleAsActiveLongTaskTimer() {
        ReportExporter exporter = new ReportExporter(observationRegistry, meterRegistry);

        exporter.export("r-4", "xlsx", 10);

        assertThat(exporter.activeTasksDuringExport).isEqualTo(1);
        LongTaskTimer active = meterRegistry.get("reports.export.active").tags("format", "xlsx").longTaskTimer();
        assertThat(active.activeTasks()).isZero();
    }

    @Test
    void failedExportIsTimedWithErrorTag() {
        ReportExporter exporter = new ReportExporter(observationRegistry, meterRegistry);

        assertThatThrownBy(() -> exporter.export("r-5", "csv", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("row count must not be negative");

        Timer failed = meterRegistry.get("reports.export")
                .tags("format", "csv", "error", "IllegalArgumentException")
                .timer();
        assertThat(failed.count()).isEqualTo(1);
        assertThat(meterRegistry.find("reports.export").tags("error", "none").timer()).isNull();
        assertThat(meterRegistry.find("reports.export.rows.written").counter()).isNull();
    }

    @Test
    void handlerConfiguredToIgnoreLongTaskTimersOnlyRecordsTimers() {
        SimpleMeterRegistry timersOnly = new SimpleMeterRegistry();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(
                        timersOnly, DefaultMeterObservationHandler.IgnoredMeters.LONG_TASK_TIMER));
        ReportExporter exporter = new ReportExporter(registry, timersOnly);

        exporter.export("r-6", "json", 3);

        assertThat(exporter.activeTasksDuringExport).isEqualTo(-1);
        assertThat(timersOnly.find("reports.export.active").longTaskTimer()).isNull();
        assertThat(timersOnly.get("reports.export").tags("format", "json").timer().count()).isEqualTo(1);
    }

    @Test
    void noopRegistryRecordsNoMeters() {
        SimpleMeterRegistry untouched = new SimpleMeterRegistry();
        ReportExporter exporter = new ReportExporter(ObservationRegistry.NOOP, untouched);

        assertThat(exporter.export("r-7", "csv", 1)).isEqualTo("r-7.csv");

        assertThat(untouched.getMeters()).isEmpty();
    }

    /** Application service that instruments each export with one observation. */
    static final class ReportExporter {

        private final ObservationRegistry registry;

        private final MeterRegistry meters;

        private int activeTasksDuringExport = -1;

        ReportExporter(ObservationRegistry registry, MeterRegistry meters) {
            this.registry = registry;
            this.meters = meters;
        }

        String export(String reportId, String format, int rows) {
            Observation observation = Observation.createNotStarted("reports.export", registry)
                    .contextualName("export " + format)
                    .lowCardinalityKeyValue("format", format)
                    .highCardinalityKeyValue("report.id", reportId);
            return observation.observe(() -> {
                if (rows < 0) {
                    throw new IllegalArgumentException("row count must not be negative");
                }
                LongTaskTimer active = meters.find("reports.export.active").tags("format", format).longTaskTimer();
                activeTasksDuringExport = active == null ? -1 : active.activeTasks();
                observation.event(Observation.Event.of("rows.written", "rows written: " + rows));
                return reportId + "." + format;
            });
        }
    }
}
