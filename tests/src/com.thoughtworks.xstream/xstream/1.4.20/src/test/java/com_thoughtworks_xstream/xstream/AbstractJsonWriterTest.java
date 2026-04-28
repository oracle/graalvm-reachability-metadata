/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringWriter;

import com.thoughtworks.xstream.io.json.JsonWriter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJsonWriterTest {
    @Test
    void writesNumericValuesAndPreservesUnsafeLongPrecision() {
        StringWriter output = new StringWriter();
        JsonWriter writer = new JsonWriter(output, JsonWriter.IEEE_754_MODE, compactFormat());

        writer.startNode("metrics", Metrics.class);
        writer.startNode("safe", Integer.class);
        writer.setValue("7");
        writer.endNode();
        writer.startNode("wide", Long.class);
        writer.setValue("9007199254740993");
        writer.endNode();
        writer.endNode();
        writer.flush();

        assertThat(output.toString())
            .contains("\"metrics\"")
            .contains("\"safe\":7")
            .contains("\"wide\":\"9007199254740993\"");
    }

    private static JsonWriter.Format compactFormat() {
        return new JsonWriter.Format(new char[0], new char[0], 0);
    }

    public static final class Metrics {
    }
}
