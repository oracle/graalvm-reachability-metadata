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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class QuantilesItemsSketchTest {
    @Test
    void returnsTypedQuantileArrays() {
        ItemsSketch<String> sketch = ItemsSketch.getInstance(Comparator.naturalOrder());
        sketch.update("alpha");
        sketch.update("charlie");
        sketch.update("bravo");
        sketch.update("delta");

        String[] quantiles = sketch.getQuantiles(new double[] {0.0, 0.5, 1.0});

        assertThat(quantiles).hasSize(3);
        assertThat(quantiles[0]).isEqualTo("alpha");
        assertThat(quantiles[1]).isIn("bravo", "charlie");
        assertThat(quantiles[2]).isEqualTo("delta");
        assertThat(sketch.getQuantile(0.5)).isEqualTo(quantiles[1]);
    }
}
