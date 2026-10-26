/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.ArrayOfStringsSerDe;
import org.apache.pulsar.shade.com.yahoo.sketches.frequencies.ItemsSketch;
import org.junit.jupiter.api.Test;

public class ReversePurgeItemHashMapTest {
    @Test
    void serializesTheActiveStringKeys() {
        ItemsSketch<String> sketch = new ItemsSketch<>(16);
        sketch.update("pulsar", 4);
        sketch.update("bookkeeper", 2);

        byte[] serialized = sketch.toByteArray(new ArrayOfStringsSerDe());

        assertThat(serialized).isNotEmpty();
    }
}
