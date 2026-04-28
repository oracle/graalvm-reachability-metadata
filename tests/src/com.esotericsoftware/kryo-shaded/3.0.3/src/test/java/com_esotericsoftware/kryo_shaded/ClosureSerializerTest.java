/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import org.junit.jupiter.api.Test;

public class ClosureSerializerTest {
    @Test
    void serializesAndDeserializesSerializableLambda() {
        Kryo kryo = newKryo();
        ClosureSerializer serializer = new ClosureSerializer();
        TextTransformer transformer = prefixedTransformer("read:");

        Output output = new Output(256, -1);
        serializer.write(kryo, output, transformer);
        kryo.reset();

        Input input = new Input(output.toBytes());
        TextTransformer read = (TextTransformer) serializer.read(kryo, input, transformer.getClass());

        assertThat(read.transform("closure")).isEqualTo("read:closure");
    }

    @Test
    void copiesSerializableLambdaThroughSerializedLambdaReplacement() {
        Kryo kryo = newKryo();
        ClosureSerializer serializer = new ClosureSerializer();
        TextTransformer transformer = prefixedTransformer("copy:");

        TextTransformer copy = (TextTransformer) serializer.copy(kryo, transformer);

        assertThat(copy.transform("closure")).isEqualTo("copy:closure");
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.register(SerializedLambda.class, new SerializedLambdaSerializer());
        return kryo;
    }

    private static TextTransformer prefixedTransformer(String prefix) {
        return value -> prefix + value;
    }

    public interface TextTransformer extends Serializable {
        String transform(String value);
    }

    private static final class SerializedLambdaSerializer extends Serializer<SerializedLambda> {
        @Override
        public void write(Kryo kryo, Output output, SerializedLambda object) {
            output.writeString(object.getFunctionalInterfaceClass());
            output.writeString(object.getFunctionalInterfaceMethodName());
            output.writeString(object.getFunctionalInterfaceMethodSignature());
            output.writeInt(object.getImplMethodKind());
            output.writeString(object.getImplClass());
            output.writeString(object.getImplMethodName());
            output.writeString(object.getImplMethodSignature());
            output.writeString(object.getInstantiatedMethodType());
            output.writeInt(object.getCapturedArgCount(), true);
            for (int index = 0; index < object.getCapturedArgCount(); index++) {
                kryo.writeClassAndObject(output, object.getCapturedArg(index));
            }
        }

        @Override
        public SerializedLambda read(Kryo kryo, Input input, Class<SerializedLambda> type) {
            String functionalInterfaceClass = input.readString();
            String functionalInterfaceMethodName = input.readString();
            String functionalInterfaceMethodSignature = input.readString();
            int implMethodKind = input.readInt();
            String implClass = input.readString();
            String implMethodName = input.readString();
            String implMethodSignature = input.readString();
            String instantiatedMethodType = input.readString();
            int capturedArgCount = input.readInt(true);
            Object[] capturedArgs = new Object[capturedArgCount];
            for (int index = 0; index < capturedArgCount; index++) {
                capturedArgs[index] = kryo.readClassAndObject(input);
            }
            return new SerializedLambda(
                    ClosureSerializerTest.class,
                    functionalInterfaceClass,
                    functionalInterfaceMethodName,
                    functionalInterfaceMethodSignature,
                    implMethodKind,
                    implClass,
                    implMethodName,
                    implMethodSignature,
                    instantiatedMethodType,
                    capturedArgs);
        }
    }
}
