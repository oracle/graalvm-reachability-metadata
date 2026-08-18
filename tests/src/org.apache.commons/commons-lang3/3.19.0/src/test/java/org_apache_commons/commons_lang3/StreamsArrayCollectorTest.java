/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.stream.Streams;
import org.junit.jupiter.api.Test;

public class StreamsArrayCollectorTest {

    @Test
    public void toArrayCollectsElementsIntoTypedArrayInEncounterOrder() {
        String[] result = Stream.of("alpha", "beta", "gamma").collect(Streams.toArray(String.class));

        assertThat(result).isExactlyInstanceOf(String[].class).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    public void arrayCollectorFinisherCreatesTypedArrayForLists() {
        Streams.ArrayCollector<Number> collector = new Streams.ArrayCollector<>(Number.class);

        Number[] result = collector.finisher().apply(List.of(1, 2.5d, 3L));

        assertThat(result).isExactlyInstanceOf(Number[].class).containsExactly(1, 2.5d, 3L);
    }

    @Test
    public void toArrayCreatesTypedEmptyArrayForEmptyStreams() {
        String[] result = Stream.<String>empty().collect(Streams.toArray(String.class));

        assertThat(result).isExactlyInstanceOf(String[].class).isEmpty();
    }
}
