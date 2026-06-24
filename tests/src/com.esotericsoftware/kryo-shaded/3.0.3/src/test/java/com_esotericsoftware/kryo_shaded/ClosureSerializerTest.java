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
import com.esotericsoftware.kryo.util.Util;
import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClosureSerializerTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesAndRestoresClosureLikeObject() {
        Kryo kryo = newKryo();
        ClosureSerializer closureSerializer = new ClosureSerializer();
        TextTransformer transformer = new ClosureLikeTransformer("hello ", "!");

        Output output = new Output(256, -1);
        kryo.writeObject(output, transformer, closureSerializer);
        output.close();

        Input input = new Input(output.toBytes());
        TextTransformer restored = kryo.readObject(input, TextTransformer.class, closureSerializer);
        input.close();

        assertThat(restored.transform("kryo")).isEqualTo("hello kryo!");
    }

    @Test
    void copiesClosureLikeObjectThroughSerializedLambdaReadResolve() {
        Kryo kryo = newKryo();
        ClosureSerializer closureSerializer = new ClosureSerializer();
        TextTransformer transformer = new ClosureLikeTransformer("native ", " image");

        TextTransformer copy = kryo.copy(transformer, closureSerializer);

        assertThat(copy.transform("test")).isEqualTo("native test image");
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        kryo.setReferences(false);
        kryo.register(SerializedLambda.class, new SerializedLambdaSerializer());
        return kryo;
    }

    @SuppressWarnings("checkstyle:MethodName")
    private static Object $deserializeLambda$(SerializedLambda serializedLambda) {
        assertThat(serializedLambda.getCapturingClass())
                .isEqualTo(ClosureSerializerTest.class.getName().replace('.', '/'));
        assertThat(serializedLambda.getCapturedArgCount()).isEqualTo(2);
        String prefix = (String) serializedLambda.getCapturedArg(0);
        String suffix = (String) serializedLambda.getCapturedArg(1);
        return new ClosureLikeTransformer(prefix, suffix);
    }

    @FunctionalInterface
    private interface TextTransformer extends Serializable {
        String transform(String text);
    }

    private static final class ClosureLikeTransformer implements TextTransformer {
        private final String prefix;
        private final String suffix;

        private ClosureLikeTransformer(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public String transform(String text) {
            return prefix + text + suffix;
        }

        private Object writeReplace() {
            return new SerializedLambda(ClosureSerializerTest.class, internalName(TextTransformer.class), "transform",
                    "(Ljava/lang/String;)Ljava/lang/String;", MethodHandleInfo.REF_invokeStatic,
                    internalName(ClosureSerializerTest.class), "rebuildTransformer",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    "(Ljava/lang/String;)Ljava/lang/String;", new Object[] { prefix, suffix });
        }
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static final class SerializedLambdaSerializer extends Serializer<SerializedLambda> {
        @Override
        public void write(Kryo kryo, Output output, SerializedLambda object) {
            output.writeString(object.getFunctionalInterfaceClass());
            output.writeString(object.getFunctionalInterfaceMethodName());
            output.writeString(object.getFunctionalInterfaceMethodSignature());
            output.writeInt(object.getImplMethodKind(), true);
            output.writeString(object.getImplClass());
            output.writeString(object.getImplMethodName());
            output.writeString(object.getImplMethodSignature());
            output.writeString(object.getInstantiatedMethodType());
            int capturedArgumentCount = object.getCapturedArgCount();
            output.writeInt(capturedArgumentCount, true);
            for (int i = 0; i < capturedArgumentCount; i++) {
                kryo.writeClassAndObject(output, object.getCapturedArg(i));
            }
        }

        @Override
        public SerializedLambda read(Kryo kryo, Input input, Class<SerializedLambda> type) {
            String functionalInterfaceClass = input.readString();
            String functionalInterfaceMethodName = input.readString();
            String functionalInterfaceMethodSignature = input.readString();
            int implMethodKind = input.readInt(true);
            String implClass = input.readString();
            String implMethodName = input.readString();
            String implMethodSignature = input.readString();
            String instantiatedMethodType = input.readString();
            Object[] capturedArguments = readCapturedArguments(kryo, input);
            return new SerializedLambda(ClosureSerializerTest.class, functionalInterfaceClass,
                    functionalInterfaceMethodName, functionalInterfaceMethodSignature, implMethodKind, implClass,
                    implMethodName, implMethodSignature, instantiatedMethodType, capturedArguments);
        }

        private static Object[] readCapturedArguments(Kryo kryo, Input input) {
            int capturedArgumentCount = input.readInt(true);
            Object[] capturedArguments = new Object[capturedArgumentCount];
            for (int i = 0; i < capturedArgumentCount; i++) {
                capturedArguments[i] = kryo.readClassAndObject(input);
            }
            return capturedArguments;
        }
    }
}
