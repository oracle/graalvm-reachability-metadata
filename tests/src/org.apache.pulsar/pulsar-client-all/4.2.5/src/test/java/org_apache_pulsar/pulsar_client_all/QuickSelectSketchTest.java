/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketch;
import org.junit.jupiter.api.Test;

public class QuickSelectSketchTest {
    @Test
    void growsAndReturnsTypedSummaries() {
        String[] keys = IntStream.range(0, 40).mapToObj(index -> "key-" + index).toArray(String[]::new);
        UpdatableSketch<Double, DoubleSummary> sketch = TupleSketchSupport.sketch(keys);

        DoubleSummary[] summaries = sketch.getSummaries();

        assertThat(sketch.getRetainedEntries()).isPositive();
        assertThat(summaries).isInstanceOf(DoubleSummary[].class).isNotEmpty();
        assertThat(sketch.compact().getRetainedEntries()).isEqualTo(sketch.getRetainedEntries());
    }
}
