/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaSerializerTest {
    @Test
    void writesAndReadsObjectsUsingJavaSerializationStreams() {
        JavaSerializer serializer = new JavaSerializer();
        Output output = new Output(128, -1);

        serializer.write(new Kryo(), output, "kryo-java-serialization");

        Object restored = serializer.read(new Kryo(), new Input(output.toBytes()), String.class);

        assertThat(restored).isEqualTo("kryo-java-serialization");
    }
}
