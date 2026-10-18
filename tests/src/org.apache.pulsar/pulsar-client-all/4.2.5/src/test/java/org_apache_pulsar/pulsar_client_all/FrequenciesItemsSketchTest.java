/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.frequencies.ErrorType;
import org.apache.pulsar.shade.com.yahoo.sketches.frequencies.ItemsSketch;
import org.junit.jupiter.api.Test;

public class FrequenciesItemsSketchTest {

    @Test
    void sortsFrequentItemsIntoATypedResultArray() {
        final ItemsSketch<String> sketch = new ItemsSketch<>(16);
        sketch.update("orders", 8);
        sketch.update("events", 3);
        sketch.update("metrics", 1);

        assertThat(sketch.getFrequentItems(ErrorType.NO_FALSE_NEGATIVES))
                .extracting(ItemsSketch.Row::getItem)
                .contains("orders", "events", "metrics");
    }
}
