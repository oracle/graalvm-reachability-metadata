/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_xmemcached.xmemcached;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.rubyeye.xmemcached.transcoders.CachedData;
import net.rubyeye.xmemcached.transcoders.SerializingTranscoder;
import org.junit.jupiter.api.Test;

public class BaseSerializingTranscoderTest {
    @Test
    void serializesAndDeserializesObjectValues() {
        List<String> value = new ArrayList<>();
        value.add("cached");
        value.add("value");
        SerializingTranscoder transcoder = new SerializingTranscoder();

        CachedData encoded = transcoder.encode(value);

        assertThat(encoded.getFlag() & SerializingTranscoder.SERIALIZED).isNotZero();
        assertThat(transcoder.decode(encoded)).isEqualTo(value);
    }
}
