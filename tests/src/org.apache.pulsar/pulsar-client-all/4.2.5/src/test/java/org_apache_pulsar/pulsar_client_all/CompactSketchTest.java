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
import org.junit.jupiter.api.Test;

public class CompactSketchTest {
    @Test
    void compactsKeysAndSummariesIntoTypedArrays() {
        CompactSketch<DoubleSummary> compact =
                TupleSketchSupport.sketch("one", "two", "three").compact();

        assertThat(compact.getRetainedEntries()).isEqualTo(3);
        assertThat(compact.getSummaries()).isInstanceOf(DoubleSummary[].class).hasSize(3);
    }
}
