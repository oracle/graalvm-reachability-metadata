/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.Streams;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class LangStreamsInnerArrayCollectorTest {
    @Test
    void collectsAParallelStreamIntoATypedArrayInEncounterOrder() {
        String[] values = Stream.of("queue", "worker", "result").parallel()
                .collect(new Streams.ArrayCollector<>(String.class));
        assertThat(values).containsExactly("queue", "worker", "result");
        String[] empty = Stream.<String>empty().collect(new Streams.ArrayCollector<>(String.class));
        assertThat(empty).isInstanceOf(String[].class).isEmpty();
    }
}
