/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.state.internals.Maybe;
import org.apache.kafka.streams.state.internals.Murmur3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises deterministic low-level state utilities through their public contracts. */
public class LowLevelStateApiCoverageTest {
    @Test
    void shouldProduceTheSameHashForWholeAndChunkedInput() {
        byte[] value = {1, 2, 3, 4, 5, 6, 7};
        Murmur3.IncrementalHash32 incremental = new Murmur3.IncrementalHash32();
        incremental.start(Murmur3.DEFAULT_SEED);
        incremental.add(value, 0, 3);
        incremental.add(value, 3, value.length - 3);
        assertThat(incremental.end()).isEqualTo(Murmur3.hash32(value));
    }

    @Test
    void shouldDistinguishDefinedAndUndefinedValues() {
        Maybe<String> defined = Maybe.defined("value");
        Maybe<String> undefined = Maybe.undefined();
        assertThat(defined.isDefined()).isTrue();
        assertThat(defined.getNullableValue()).isEqualTo("value");
        assertThat(undefined.isDefined()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(undefined::getNullableValue)
                .isInstanceOf(java.util.NoSuchElementException.class);
        assertThat(defined).isNotEqualTo(undefined);
        assertThat(defined.hashCode()).isEqualTo(Maybe.defined("value").hashCode());
        assertThat(defined.toString()).contains("value");
    }
}
