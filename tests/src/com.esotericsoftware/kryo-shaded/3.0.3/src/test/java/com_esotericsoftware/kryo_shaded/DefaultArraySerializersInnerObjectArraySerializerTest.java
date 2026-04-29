/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;

public class DefaultArraySerializersInnerObjectArraySerializerTest {
    @Test
    void readsObjectArrayWithRuntimeComponentType() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        CharSequence[] original = {
                new StringBuilder("alpha"),
                "beta",
                new StringBuilder("gamma"),
                null
        };

        Output output = new Output(256, -1);
        kryo.writeObjectOrNull(output, original, CharSequence[].class);
        kryo.reset();

        Input input = new Input(output.toBytes());
        CharSequence[] read = kryo.readObjectOrNull(input, CharSequence[].class);

        assertThat(read).isInstanceOf(CharSequence[].class);
        assertThat(read).hasSize(original.length);
        assertThat(read[0]).isInstanceOf(StringBuilder.class).hasToString("alpha");
        assertThat(read[1]).isInstanceOf(String.class).hasToString("beta");
        assertThat(read[2]).isInstanceOf(StringBuilder.class).hasToString("gamma");
        assertThat(read[3]).isNull();
    }
}
