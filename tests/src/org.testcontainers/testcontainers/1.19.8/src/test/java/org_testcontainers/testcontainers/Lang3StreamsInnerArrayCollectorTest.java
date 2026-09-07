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

public class Lang3StreamsInnerArrayCollectorTest {
    @Test
    void collectsAStreamIntoATypedArray() {
        String[] result = Stream.of("a", "b").collect(Streams.toArray(String.class));

        assertThat(result).containsExactly("a", "b");
    }
}
