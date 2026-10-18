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
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.Intersection;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketchBuilder;
import org.junit.jupiter.api.Test;

public class IntersectionTest {

    @Test
    void intersectsTupleSketchesAndCombinesMatchingSummaries() {
        final UpdatableSketch<Double, DoubleSummary> left = sketch();
        left.update("orders", 2.0);
        left.update("audit", 1.0);
        final UpdatableSketch<Double, DoubleSummary> right = sketch();
        right.update("orders", 3.0);
        right.update("metrics", 4.0);
        final Intersection<DoubleSummary> intersection = new Intersection<>(new DoubleSummaryFactory());

        intersection.update(left);
        intersection.update(right);
        final CompactSketch<DoubleSummary> result = intersection.getResult();

        assertThat(result.getRetainedEntries()).isEqualTo(1);
        assertThat(result.getSummaries()).hasSize(1);
        assertThat(result.getSummaries()[0].getValue()).isEqualTo(5.0);
    }

    private static UpdatableSketch<Double, DoubleSummary> sketch() {
        return new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
    }
}
