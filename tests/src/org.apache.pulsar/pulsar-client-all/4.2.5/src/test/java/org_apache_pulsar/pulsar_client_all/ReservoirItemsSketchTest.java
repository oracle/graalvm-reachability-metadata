/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.yahoo.sketches.sampling.ReservoirItemsSketch;
import org.junit.jupiter.api.Test;

public class ReservoirItemsSketchTest {
    @Test
    void returnsSamplesWithInferredAndRequestedArrayTypes() {
        ReservoirItemsSketch<CharSequence> sketch = ReservoirItemsSketch.getInstance(8);
        sketch.update("one");
        sketch.update("two");
        sketch.update("three");

        CharSequence[] inferred = sketch.getSamples();
        CharSequence[] requested = sketch.getSamples(CharSequence.class);

        assertThat(inferred).isInstanceOf(String[].class).containsExactly("one", "two", "three");
        assertThat(requested)
                .isInstanceOf(CharSequence[].class)
                .containsExactly("one", "two", "three");
    }
}
