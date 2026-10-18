/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import org.apache.pulsar.shade.com.yahoo.sketches.quantiles.ItemsSketch;
import org.junit.jupiter.api.Test;

public class QuantilesItemsSketchTest {

    @Test
    void calculatesTypedQuantileArrays() {
        final ItemsSketch<String> sketch = ItemsSketch.getInstance(Comparator.naturalOrder());
        sketch.update("alpha");
        sketch.update("charlie");
        sketch.update("bravo");

        assertThat(sketch.getQuantiles(new double[] {0.0, 0.5, 1.0}))
                .containsExactly("alpha", "bravo", "charlie");
    }
}
