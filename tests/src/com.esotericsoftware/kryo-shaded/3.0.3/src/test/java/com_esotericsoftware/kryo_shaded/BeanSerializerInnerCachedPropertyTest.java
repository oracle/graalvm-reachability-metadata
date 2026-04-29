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
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import org.junit.jupiter.api.Test;

public class BeanSerializerInnerCachedPropertyTest {
    @Test
    void serializesReadsAndCopiesBeanPropertiesUsingReflectionFallback() {
        Kryo kryo = newKryo();
        BeanSerializer<FallbackBean> serializer = new BeanSerializer<>(kryo, FallbackBean.class);
        FallbackBean original = new FallbackBean("sample-name", 37);

        FallbackBean read = roundTrip(kryo, serializer, original);
        FallbackBean copy = serializer.copy(kryo, original);

        assertThat(read.getName()).isEqualTo(original.getName());
        assertThat(read.getCount()).isEqualTo(original.getCount());
        assertThat(copy.getName()).isEqualTo(original.getName());
        assertThat(copy.getCount()).isEqualTo(original.getCount());
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    private static FallbackBean roundTrip(Kryo kryo, BeanSerializer<FallbackBean> serializer, FallbackBean original) {
        Output output = new Output(128, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        FallbackBean read = serializer.read(kryo, input, FallbackBean.class);
        kryo.reset();
        return read;
    }

    public static class FallbackBean {
        private String name;
        private int count;

        public FallbackBean() {
        }

        FallbackBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class FallbackBeanMethodAccess {
        public FallbackBeanMethodAccess() {
        }
    }
}
