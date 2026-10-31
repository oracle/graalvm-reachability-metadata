/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.HdrHistogram.Histogram;
import com.couchbase.client.core.deps.org.HdrHistogram.HistogramLogReader;
import com.couchbase.client.core.deps.org.HdrHistogram.HistogramLogWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Base64HelperTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Histogram histogram = new Histogram(10000, 3);
        histogram.recordValueWithCount(200, 5);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HistogramLogWriter writer = new HistogramLogWriter(bytes);
        writer.outputLogFormatVersion();
        writer.outputLegend();
        writer.outputIntervalHistogram(1.0, 2.0, histogram);
        writer.close();
        try (HistogramLogReader reader = new HistogramLogReader(new ByteArrayInputStream(bytes.toByteArray()))) {
            Histogram decoded = (Histogram) reader.nextIntervalHistogram();
            assertNotNull(decoded);
            assertEquals(5, decoded.getTotalCount());
            assertEquals(5, decoded.getCountAtValue(200));
            assertNull(reader.nextIntervalHistogram());
        }
    }
}
