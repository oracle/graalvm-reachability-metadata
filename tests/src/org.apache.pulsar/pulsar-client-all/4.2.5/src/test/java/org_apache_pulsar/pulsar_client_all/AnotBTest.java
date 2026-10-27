/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.tuple.AnotB;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.junit.jupiter.api.Test;

public class AnotBTest {
    @Test
    void subtractsSketchesAndReturnsTypedSummaries() {
        AnotB<DoubleSummary> difference = new AnotB<>();
        difference.update(
                TupleSketchSupport.sketch("shared", "left"),
                TupleSketchSupport.sketch("shared", "right"));

        assertThat(difference.getResult().getSummaries())
                .isInstanceOf(DoubleSummary[].class)
                .hasSize(1);
    }
}
