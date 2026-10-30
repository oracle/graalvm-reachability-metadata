/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.stream.Streams;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamsInnerArrayCollectorTest {
    @Test
    void collectsAParallelStreamIntoATypedArrayInEncounterOrder() {
        Integer[] values = Stream.of(2, 3, 5).parallel().collect(new Streams.ArrayCollector<>(Integer.class));
        assertThat(values).containsExactly(2, 3, 5);
        Integer[] empty = Stream.<Integer>empty().collect(new Streams.ArrayCollector<>(Integer.class));
        assertThat(empty).isInstanceOf(Integer[].class).isEmpty();
    }
}
