/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Kryo.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import org.junit.jupiter.api.Test;
import org.objenesis.strategy.StdInstantiatorStrategy;

public class ClosureSerializerTest {
    @Test
    void serializesAndCopiesSerializableLambdaClosures() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setAsmEnabled(false);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        kryo.register(SerializedLambda.class);

        ClosureSerializer serializer = new ClosureSerializer();
        GreetingFunction original = greetingFunction("Hello");

        GreetingFunction restored = readClosure(kryo, serializer, writeClosure(kryo, serializer, original));
        GreetingFunction copied = (GreetingFunction) serializer.copy(kryo, original);

        assertThat(restored.apply("Kryo")).isEqualTo("Hello, Kryo!");
        assertThat(copied.apply("Native Image")).isEqualTo("Hello, Native Image!");
    }

    private static byte[] writeClosure(Kryo kryo, ClosureSerializer serializer, GreetingFunction closure) {
        Output output = new Output(128, -1);
        serializer.write(kryo, output, closure);
        output.close();
        return output.toBytes();
    }

    private static GreetingFunction readClosure(Kryo kryo, ClosureSerializer serializer, byte[] bytes) {
        return (GreetingFunction) serializer.read(kryo, new Input(bytes), GreetingFunction.class);
    }

    private static GreetingFunction greetingFunction(String greeting) {
        return name -> greeting + ", " + name + "!";
    }

    @FunctionalInterface
    public interface GreetingFunction extends Serializable {
        String apply(String name);
    }
}
