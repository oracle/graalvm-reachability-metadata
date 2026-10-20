/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Incremental data binding through `ObjectReader` and `ObjectWriter`: `MappingIterator` over
 * newline-delimited and array-wrapped streams, `SequenceWriter` output, `ObjectReader.at` sub-tree
 * binding, and `readerForUpdating` partial updates with `@JsonMerge`.
 *
 * A telemetry gateway ingests sensor readings one record at a time from a newline-delimited feed,
 * aggregates them per sensor and streams the per-sensor summaries back out. Its device settings
 * live in memory and are patched in place by partial JSON documents sent from an operator console.
 */
public class SensorFeedStreamingBindingTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void newlineDelimitedFeedIsBoundOneRecordAtATime() throws Exception {
        String feed = """
                {"sensor":"boiler-1","celsius":71.5,"sequence":1}
                {"sensor":"boiler-2","celsius":64.0,"sequence":2}
                {"sensor":"boiler-1","celsius":73.5,"sequence":3}
                {"sensor":"boiler-1","celsius":72.0,"sequence":4}
                """;

        Map<String, Summary> summaries = new TreeMap<>();
        int records = 0;
        try (MappingIterator<Reading> readings = mapper.readerFor(Reading.class)
                .readValues(new StringReader(feed))) {
            while (readings.hasNextValue()) {
                Reading reading = readings.nextValue();
                records++;
                assertEquals(records, reading.sequence);
                summaries.computeIfAbsent(reading.sensor, Summary::new).add(reading.celsius);
            }
        }

        assertEquals(4, records);
        assertEquals(2, summaries.size());
        Summary boiler1 = summaries.get("boiler-1");
        assertEquals(3, boiler1.count);
        assertEquals(71.5, boiler1.min);
        assertEquals(73.5, boiler1.max);
        assertEquals(72.333, boiler1.getAverage(), 0.001);
        assertEquals(1, summaries.get("boiler-2").count);
    }

    @Test
    void arrayWrappedBatchIsUnwrappedByTheIterator() throws Exception {
        String batch = """
                [
                  {"sensor":"chiller-1","celsius":6.5,"sequence":10},
                  {"sensor":"chiller-1","celsius":7.0,"sequence":11},
                  {"sensor":"chiller-2","celsius":5.5,"sequence":12}
                ]
                """;

        MappingIterator<Reading> readings = mapper.readerFor(Reading.class).readValues(batch);
        List<Reading> all = readings.readAll();

        assertEquals(3, all.size());
        assertEquals("chiller-2", all.get(2).sensor);
        assertEquals(5.5, all.get(2).celsius);
        assertFalse(readings.hasNextValue());
    }

    @Test
    void readerAtPointerBindsOnlyTheEnvelopePayload() throws Exception {
        String envelope = """
                {"gateway":"gw-7","received":1700000000,
                 "payload":{"readings":[
                   {"sensor":"pump-1","celsius":40.0,"sequence":1},
                   {"sensor":"pump-1","celsius":42.0,"sequence":2}
                 ]}}
                """;

        ObjectReader payloadReader = mapper.readerFor(Reading[].class).at("/payload/readings");
        Reading[] readings = payloadReader.readValue(envelope);

        assertEquals(2, readings.length);
        assertEquals("pump-1", readings[0].sensor);
        assertEquals(42.0, readings[1].celsius);
    }

    @Test
    void summariesAreStreamedOutAsLinesAndAsArray() throws Exception {
        Summary boiler = new Summary("boiler-1");
        boiler.add(70.0);
        boiler.add(74.0);
        Summary chiller = new Summary("chiller-1");
        chiller.add(6.0);

        ObjectWriter summaryWriter = mapper.writerFor(Summary.class);

        StringWriter lines = new StringWriter();
        try (SequenceWriter sequence = summaryWriter.withRootValueSeparator("\n").writeValues(lines)) {
            sequence.write(boiler);
            sequence.write(chiller);
        }
        String[] emitted = lines.toString().split("\n");
        assertEquals(2, emitted.length);
        assertEquals("""
                {"sensor":"boiler-1","count":2,"min":70.0,"max":74.0,"average":72.0}""", emitted[0]);
        assertEquals("""
                {"sensor":"chiller-1","count":1,"min":6.0,"max":6.0,"average":6.0}""", emitted[1]);

        StringWriter array = new StringWriter();
        try (SequenceWriter sequence = summaryWriter.writeValuesAsArray(array)) {
            sequence.writeAll(List.of(boiler, chiller));
        }
        JsonNode written = mapper.readTree(array.toString());
        assertTrue(written.isArray());
        assertEquals(2, written.size());
        assertEquals("chiller-1", written.get(1).get("sensor").asText());
        assertEquals(72.0, written.get(0).get("average").asDouble());
    }

    @Test
    void operatorPatchUpdatesSettingsInPlace() throws Exception {
        DeviceSettings settings = new DeviceSettings();
        settings.name = "boiler-1";
        settings.samplingSeconds = 30;
        settings.thresholds.warning = 80.0;
        settings.thresholds.critical = 95.0;
        settings.labels.put("site", "north");
        settings.labels.put("floor", "2");
        settings.recipients.add("ops@example.org");
        DeviceSettings.Thresholds originalThresholds = settings.thresholds;

        String patch = """
                {"samplingSeconds":15,
                 "thresholds":{"critical":90.0},
                 "labels":{"floor":"3","zone":"east"},
                 "recipients":["oncall@example.org"]}
                """;
        DeviceSettings updated = mapper.readerForUpdating(settings).readValue(patch);

        assertSame(settings, updated);
        assertEquals("boiler-1", settings.name);
        assertEquals(15, settings.samplingSeconds);
        assertSame(originalThresholds, settings.thresholds);
        assertEquals(80.0, settings.thresholds.warning);
        assertEquals(90.0, settings.thresholds.critical);
        assertEquals(Map.of("site", "north", "floor", "3", "zone", "east"), settings.labels);
        assertEquals(List.of("ops@example.org", "oncall@example.org"), settings.recipients);
    }

    @Test
    void patchWithUnknownFieldIsRejectedUnlessTheReaderRelaxesIt() throws Exception {
        DeviceSettings settings = new DeviceSettings();
        settings.name = "pump-1";
        settings.samplingSeconds = 60;
        String patch = """
                {"samplingSeconds":5,"firmware":"2.1.0"}
                """;

        ObjectReader strict = mapper.readerForUpdating(settings);
        UnrecognizedPropertyException failure = assertThrows(UnrecognizedPropertyException.class,
                () -> strict.readValue(patch));
        assertEquals("firmware", failure.getPropertyName());

        ObjectReader lenient = strict.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        lenient.readValue(patch);
        assertEquals(5, settings.samplingSeconds);
        assertEquals("pump-1", settings.name);
    }

    /** One temperature reading as it arrives on the feed. */
    public static class Reading {
        public String sensor;
        public double celsius;
        public int sequence;
    }

    /** Running aggregate for one sensor, written back out by the gateway. */
    @JsonPropertyOrder({"sensor", "count", "min", "max", "average"})
    public static class Summary {
        public final String sensor;
        public int count;
        public double min = Double.POSITIVE_INFINITY;
        public double max = Double.NEGATIVE_INFINITY;
        private double total;

        Summary(String sensor) {
            this.sensor = sensor;
        }

        void add(double celsius) {
            count++;
            total += celsius;
            min = Math.min(min, celsius);
            max = Math.max(max, celsius);
        }

        public double getAverage() {
            return total / count;
        }
    }

    /** In-memory device configuration that console patches update in place. */
    public static class DeviceSettings {
        public String name;
        public int samplingSeconds;

        @JsonMerge
        public Thresholds thresholds = new Thresholds();

        @JsonMerge
        public Map<String, String> labels = new LinkedHashMap<>();

        @JsonMerge
        public List<String> recipients = new ArrayList<>();

        /** Alert thresholds in degrees Celsius. */
        public static class Thresholds {
            public double warning;
            public double critical;
        }
    }
}
