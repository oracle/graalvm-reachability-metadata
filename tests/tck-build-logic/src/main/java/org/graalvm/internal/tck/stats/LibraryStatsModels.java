/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Strongly-typed models for artifact-indexed library stats JSON payloads.
 */
public final class LibraryStatsModels {

    private LibraryStatsModels() {
    }

    public record LibraryStats(
            Map<String, ArtifactStats> entries
    ) {
    }

    public record ArtifactStats(
            Map<String, MetadataVersionStats> metadataVersions
    ) {
    }

    public record MetadataVersionStats(
            List<VersionStats> versions
    ) {
    }

    @JsonPropertyOrder({
            "version",
            "dynamicAccess",
            "libraryCoverage"
    })
    public record VersionStats(
            String version,
            DynamicAccessStats dynamicAccess,
            LibraryCoverage libraryCoverage
    ) {
    }

    public record DynamicAccessStats(
            long totalCalls,
            long coveredCalls,
            BigDecimal coverageRatio,
            Map<String, DynamicAccessBreakdown> breakdown
    ) {
    }

    public record DynamicAccessBreakdown(
            long totalCalls,
            long coveredCalls,
            BigDecimal coverageRatio
    ) {
    }

    public record LibraryCoverage(
            CoverageMetricValue line,
            CoverageMetricValue instruction,
            CoverageMetricValue method
    ) {
        public LibraryCoverage(CoverageMetric line, CoverageMetric instruction, CoverageMetric method) {
            this(CoverageMetricValue.available(line), CoverageMetricValue.available(instruction), CoverageMetricValue.available(method));
        }
    }

    public record CoverageMetric(
            long covered,
            long missed,
            long total,
            BigDecimal ratio
    ) {
    }

    @JsonSerialize(using = CoverageMetricValueSerializer.class)
    @JsonDeserialize(using = CoverageMetricValueDeserializer.class)
    public record CoverageMetricValue(
            CoverageMetric metric
    ) {
        private static final String NOT_AVAILABLE = "N/A";

        public static CoverageMetricValue available(CoverageMetric metric) {
            return new CoverageMetricValue(metric);
        }

        public static CoverageMetricValue notAvailable() {
            return new CoverageMetricValue(null);
        }

        public boolean isAvailable() {
            return metric != null;
        }

        public long covered() {
            return requireMetric().covered();
        }

        public long missed() {
            return requireMetric().missed();
        }

        public long total() {
            return requireMetric().total();
        }

        public BigDecimal ratio() {
            return requireMetric().ratio();
        }

        private CoverageMetric requireMetric() {
            if (!isAvailable()) {
                throw new IllegalStateException("Coverage metric is not available");
            }
            return metric;
        }
    }

    public static final class CoverageMetricValueSerializer extends JsonSerializer<CoverageMetricValue> {

        @Override
        public void serialize(CoverageMetricValue value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
            if (value == null || !value.isAvailable()) {
                generator.writeString(CoverageMetricValue.NOT_AVAILABLE);
                return;
            }
            generator.writeObject(value.metric());
        }
    }

    public static final class CoverageMetricValueDeserializer extends JsonDeserializer<CoverageMetricValue> {

        @Override
        public CoverageMetricValue deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node != null && node.isTextual() && CoverageMetricValue.NOT_AVAILABLE.equals(node.textValue())) {
                return CoverageMetricValue.notAvailable();
            }
            if (node != null && node.isObject()) {
                JsonNode coveredNode = node.get("covered");
                JsonNode missedNode = node.get("missed");
                JsonNode totalNode = node.get("total");
                JsonNode ratioNode = node.get("ratio");
                if (coveredNode != null && missedNode != null && totalNode != null && ratioNode != null) {
                    return CoverageMetricValue.available(new CoverageMetric(
                            coveredNode.longValue(),
                            missedNode.longValue(),
                            totalNode.longValue(),
                            ratioNode.decimalValue()
                    ));
                }
            }
            throw JsonMappingException.from(parser, "Coverage metric must be an object or the string 'N/A'");
        }
    }
}
