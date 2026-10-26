/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummaryFactory;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.Intersection;
import org.junit.jupiter.api.Test;

public class IntersectionTest {
    @Test
    void intersectsSketchesAndReturnsTypedSummaries() {
        Intersection<DoubleSummary> intersection = new Intersection<>(new DoubleSummaryFactory());
        intersection.update(TupleSketchSupport.sketch("shared", "left"));
        intersection.update(TupleSketchSupport.sketch("shared", "right"));

        assertThat(intersection.getResult().getSummaries())
                .isInstanceOf(DoubleSummary[].class)
                .hasSize(1);
    }
}
