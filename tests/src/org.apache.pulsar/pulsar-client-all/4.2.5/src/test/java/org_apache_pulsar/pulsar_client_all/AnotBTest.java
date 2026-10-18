/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.tuple.AnotB;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.CompactSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummaryFactory;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketchBuilder;
import org.junit.jupiter.api.Test;

public class AnotBTest {

    @Test
    void subtractsMatchingKeysFromATupleSketch() {
        final UpdatableSketch<Double, DoubleSummary> left = sketch();
        left.update("orders", 2.0);
        left.update("audit", 1.0);
        final UpdatableSketch<Double, DoubleSummary> right = sketch();
        right.update("audit", 3.0);
        final AnotB<DoubleSummary> operation = new AnotB<>();

        operation.update(left, right.compact());
        final CompactSketch<DoubleSummary> result = operation.getResult();

        assertThat(result.getRetainedEntries()).isEqualTo(1);
        assertThat(result.getSummaries()).hasSize(1);
        assertThat(result.getSummaries()[0].getValue()).isEqualTo(2.0);
    }

    private static UpdatableSketch<Double, DoubleSummary> sketch() {
        return new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
    }
}
