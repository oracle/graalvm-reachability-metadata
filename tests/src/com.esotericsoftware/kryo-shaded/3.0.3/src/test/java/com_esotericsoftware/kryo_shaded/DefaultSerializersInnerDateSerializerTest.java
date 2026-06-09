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
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import java.util.Date;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerDateSerializerTest {
    @Test
    void readsDateSubclassWithPublicLongConstructor() {
        Kryo kryo = new Kryo();
        kryo.register(HistoricDate.class, new DefaultSerializers.DateSerializer());

        HistoricDate original = new HistoricDate(1_234_567_890L);
        Output output = new Output(64, -1);
        kryo.writeObject(output, original);
        output.close();

        HistoricDate restored = kryo.readObject(new Input(output.toBytes()), HistoricDate.class);

        assertThat(restored).isInstanceOf(HistoricDate.class);
        assertThat(restored.getTime()).isEqualTo(original.getTime());
        assertThat(restored.isConstructedWithLong()).isTrue();
    }

    public static class HistoricDate extends Date {
        private final boolean constructedWithLong;

        public HistoricDate(long time) {
            super(time);
            constructedWithLong = true;
        }

        public boolean isConstructedWithLong() {
            return constructedWithLong;
        }
    }
}
