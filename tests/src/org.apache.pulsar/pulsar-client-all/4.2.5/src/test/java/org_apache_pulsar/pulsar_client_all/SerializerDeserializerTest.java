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
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummaryFactory;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.Sketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.Sketches;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketchBuilder;
import org.junit.jupiter.api.Test;

public class SerializerDeserializerTest {

    @Test
    void restoresSummaryAndFactoryTypesFromSerializedSketchData() {
        final UpdatableSketch<Double, DoubleSummary> source =
                new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
        source.update("orders", 2.5);
        source.update("orders", 1.5);
        source.update("audit", 3.0);

        final byte[] serialized = source.toByteArray();
        final Sketch<DoubleSummary> restored = Sketches.heapifySketch(new NativeMemory(serialized));

        assertThat(restored.getRetainedEntries()).isEqualTo(2);
        assertThat(restored.getSummaries())
                .allSatisfy(summary -> assertThat(summary).isInstanceOf(DoubleSummary.class));
        assertThat(restored.getSummaries()).extracting(DoubleSummary::getValue).containsExactlyInAnyOrder(4.0, 3.0);
    }
}
