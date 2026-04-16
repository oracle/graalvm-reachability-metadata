/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.apache.commons.lang3.stream.Streams;
import org.junit.jupiter.api.Test;

public class StreamsArrayCollectorTest {

    @Test
    void collectsStreamElementsIntoATypedArray() {
        final String[] collected = Stream.of("alpha", "beta", "gamma")
                .collect(Streams.toArray(String.class));

        assertThat(collected).containsExactly("alpha", "beta", "gamma");
        assertThat(collected.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void collectsEmptyStreamsIntoAnEmptyTypedArray() {
        final String[] collected = Stream.<String>empty()
                .collect(Streams.toArray(String.class));

        assertThat(collected).isEmpty();
        assertThat(collected.getClass().getComponentType()).isEqualTo(String.class);
    }
}
