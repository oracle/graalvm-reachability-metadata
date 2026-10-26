/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.memory.NativeMemory;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.Sketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.Sketches;
import org.junit.jupiter.api.Test;

public class SerializerDeserializerTest {
    @Test
    void roundTripsTupleSketchFactoriesAndSummaries() {
        byte[] serialized = TupleSketchSupport.sketch("one", "two", "three").toByteArray();

        Sketch<DoubleSummary> restored = Sketches.heapifySketch(new NativeMemory(serialized));

        assertThat(restored.getRetainedEntries()).isEqualTo(3);
        assertThat(restored.getSummaries()).isInstanceOf(DoubleSummary[].class).hasSize(3);
    }
}
