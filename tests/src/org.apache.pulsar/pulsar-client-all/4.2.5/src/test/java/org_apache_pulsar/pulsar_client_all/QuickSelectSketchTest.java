/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.tuple.CompactSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummaryFactory;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketchBuilder;
import org.junit.jupiter.api.Test;

public class QuickSelectSketchTest {

    @Test
    void growsAndCompactsATupleSketchWithTypedSummaries() {
        final UpdatableSketch<Double, DoubleSummary> sketch =
                new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory())
                        .setNominalEntries(16)
                        .build();
        for (int index = 0; index < 40; index++) {
            sketch.update("key-" + index, (double) index);
        }

        final DoubleSummary[] summaries = sketch.getSummaries();
        final CompactSketch<DoubleSummary> compact = sketch.compact();

        assertThat(sketch.getRetainedEntries()).isGreaterThan(0);
        assertThat(summaries).hasSize(sketch.getRetainedEntries());
        assertThat(summaries.getClass()).isEqualTo(DoubleSummary[].class);
        assertThat(compact.getSummaries()).hasSize(compact.getRetainedEntries());
        assertThat(compact.getSummaries().getClass()).isEqualTo(DoubleSummary[].class);
    }
}
