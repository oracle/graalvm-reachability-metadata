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
import org.apache.pulsar.shade.com.yahoo.sketches.frequencies.ItemsSketch.Row;
import org.junit.jupiter.api.Test;

public class FrequenciesItemsSketchTest {
    @Test
    void returnsTypedRowsForFrequentItems() {
        ItemsSketch<String> sketch = new ItemsSketch<>(16);
        sketch.update("pulsar", 4);
        sketch.update("bookkeeper", 2);

        Row<String>[] rows = sketch.getFrequentItems(ErrorType.NO_FALSE_NEGATIVES);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(Row::getItem).containsExactlyInAnyOrder("pulsar", "bookkeeper");
    }
}
