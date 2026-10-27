/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Pair;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.KTypesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlin.text.StringsKt;
import org.junit.jupiter.api.Test;

/**
 * Kotlin-to-Java generic type conversion: a JSON-RPC endpoint decodes the arguments of exposed
 * Kotlin functions by converting each parameter `KType` to a `java.lang.reflect.Type` with
 * `ReflectJvmMapping.getJavaType` and letting Jackson bind the JSON value to exactly that type.
 *
 * The conversion keeps what the endpoint needs to decode faithfully: `Int` becomes the primitive
 * `int` while `Int?` becomes `Integer`, a `vararg` becomes a Java array, and generic collection
 * parameters keep their type arguments, wildcards and type variables, so a JSON array of objects
 * becomes a list of `kotlin.Pair`s that the Jackson Kotlin module builds through the Kotlin
 * constructor. Arguments the caller omits take their Kotlin defaults through `callBy`.
 */
class JsonRpcTypeBinderTest {

    /** JSON-RPC endpoint that exposes Kotlin functions under operation names. */
    static final class JsonRpcEndpoint {

        /** JSON key under which a caller passes the extension receiver of an operation. */
        static final String RECEIVER = "receiver";

        private final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule.Builder().build());
        private final Map<String, KFunction<?>> operations = new LinkedHashMap<>();

        /** Exposes the Kotlin declaration of `method` as operation `name`. */
        JsonRpcEndpoint expose(String name, Method method) {
            KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
            if (function == null) {
                throw new IllegalStateException(method + " has no Kotlin declaration");
            }
            // Multifile facades delegate to package-private parts, which kotlin-reflect calls.
            KCallablesJvm.setAccessible(function, true);
            operations.put(name, function);
            return this;
        }

        /** The Java type the endpoint decodes each named argument into. */
        Map<String, Type> argumentTypes(String operation) {
            Map<String, Type> types = new LinkedHashMap<>();
            for (KParameter parameter : operation(operation).getParameters()) {
                types.put(argumentName(parameter), ReflectJvmMapping.getJavaType(parameter.getType()));
            }
            return types;
        }

        /** The Java type the endpoint encodes the result of `operation` from. */
        Type resultType(String operation) {
            return ReflectJvmMapping.getJavaType(operation(operation).getReturnType());
        }

        /** The operation descriptor the endpoint publishes for client generators. */
        String describe(String operation) {
            KFunction<?> function = operation(operation);
            List<String> arguments = new ArrayList<>();
            for (KParameter parameter : function.getParameters()) {
                arguments.add(argumentName(parameter) + ": "
                        + ReflectJvmMapping.getJavaType(parameter.getType()).getTypeName()
                        + (parameter.isVararg() ? " (vararg)" : "")
                        + (parameter.isOptional() ? " = default" : ""));
            }
            return operation + "(" + String.join(", ", arguments) + "): " + resultType(operation).getTypeName();
        }

        /** Decodes `request` into the operation's arguments, calls it, and encodes the result. */
        String call(String operation, String request) throws JsonProcessingException {
            KFunction<?> function = operation(operation);
            JsonNode arguments = mapper.readTree(request);
            Map<KParameter, Object> bound = new HashMap<>();
            for (KParameter parameter : function.getParameters()) {
                String name = argumentName(parameter);
                JsonNode value = arguments.get(name);
                if (value == null) {
                    if (!parameter.isOptional()) {
                        throw new IllegalArgumentException(operation + ": missing argument " + name);
                    }
                    continue;
                }
                bound.put(parameter, decode(operation, name, parameter.getType(), value));
            }
            Object result = function.callBy(bound);
            return mapper.writerFor(mapper.constructType(resultType(operation))).writeValueAsString(result);
        }

        private Object decode(String operation, String name, KType type, JsonNode value) {
            if (value.isNull()) {
                if (!type.isMarkedNullable()) {
                    throw new IllegalArgumentException(operation + ": argument " + name + " must not be null");
                }
                return null;
            }
            KClass<?> erased = KTypesJvm.getJvmErasure(type);
            boolean iterable = KClasses.isSubclassOf(erased, kotlin(Iterable.class))
                    || JvmClassMappingKt.getJavaClass(erased).isArray();
            if (iterable && !value.isArray()) {
                throw new IllegalArgumentException(operation + ": argument " + name + " must be a JSON array");
            }
            JavaType target = mapper.constructType(ReflectJvmMapping.getJavaType(type));
            try {
                return mapper.readerFor(target).readValue(value);
            } catch (IOException e) {
                throw new IllegalArgumentException(operation + ": argument " + name + " is not a " + target, e);
            }
        }

        private KFunction<?> operation(String operation) {
            KFunction<?> function = operations.get(operation);
            if (function == null) {
                throw new IllegalArgumentException("unknown operation " + operation);
            }
            return function;
        }

        private static String argumentName(KParameter parameter) {
            return parameter.getKind() == KParameter.Kind.EXTENSION_RECEIVER ? RECEIVER : parameter.getName();
        }
    }

    private static JsonRpcEndpoint textAndCollectionEndpoint() throws NoSuchMethodException {
        return new JsonRpcEndpoint()
                .expose("padStart", StringsKt.class.getMethod("padStart", String.class, int.class, char.class))
                .expose("split", StringsKt.class.getMethod("split", CharSequence.class, String[].class,
                        boolean.class, int.class))
                .expose("parseInt", StringsKt.class.getMethod("toIntOrNull", String.class, int.class))
                .expose("windowed", CollectionsKt.class.getMethod("windowed", Iterable.class, int.class, int.class,
                        boolean.class))
                .expose("index", MapsKt.class.getMethod("toMap", Iterable.class));
    }

    @Test
    void convertsKotlinParameterTypesToJavaTypes() throws NoSuchMethodException {
        JsonRpcEndpoint endpoint = textAndCollectionEndpoint();

        Map<String, Type> padStart = endpoint.argumentTypes("padStart");
        assertThat(padStart).containsExactly(
                Map.entry("receiver", String.class), Map.entry("length", int.class), Map.entry("padChar", char.class));
        assertThat(endpoint.argumentTypes("split")).containsEntry("delimiters", String[].class);
        assertThat(endpoint.argumentTypes("parseInt")).containsEntry("radix", int.class);
        assertThat(endpoint.resultType("parseInt")).isEqualTo(Integer.class);

        ParameterizedType split = (ParameterizedType) endpoint.resultType("split");
        assertThat(split.getRawType()).isEqualTo(List.class);
        assertThat(split.getActualTypeArguments()).containsExactly(String.class);

        ParameterizedType pairs = (ParameterizedType) endpoint.argumentTypes("index").get("receiver");
        assertThat(pairs.getRawType()).isEqualTo(Iterable.class);
        WildcardType element = (WildcardType) pairs.getActualTypeArguments()[0];
        ParameterizedType pair = (ParameterizedType) element.getUpperBounds()[0];
        assertThat(pair.getRawType()).isEqualTo(Pair.class);
        WildcardType key = (WildcardType) pair.getActualTypeArguments()[0];
        assertThat(((TypeVariable<?>) key.getUpperBounds()[0]).getName()).isEqualTo("K");

        ParameterizedType windows = (ParameterizedType) endpoint.resultType("windowed");
        ParameterizedType window = (ParameterizedType) windows.getActualTypeArguments()[0];
        assertThat(window.getRawType()).isEqualTo(List.class);
        assertThat(((TypeVariable<?>) window.getActualTypeArguments()[0]).getName()).isEqualTo("T");
    }

    @Test
    void publishesOperationDescriptorsInJavaTerms() throws NoSuchMethodException {
        JsonRpcEndpoint endpoint = textAndCollectionEndpoint();

        assertThat(endpoint.describe("padStart"))
                .isEqualTo("padStart(receiver: java.lang.String, length: int, padChar: char = default):"
                        + " java.lang.String");
        assertThat(endpoint.describe("split")).isEqualTo("split(receiver: java.lang.CharSequence,"
                + " delimiters: java.lang.String[] (vararg), ignoreCase: boolean = default, limit: int = default):"
                + " java.util.List<java.lang.String>");
        assertThat(endpoint.describe("windowed")).isEqualTo("windowed(receiver: java.lang.Iterable<? extends T>,"
                + " size: int, step: int = default, partialWindows: boolean = default):"
                + " java.util.List<java.util.List<T>>");
        assertThat(endpoint.describe("index")).isEqualTo("index(receiver:"
                + " java.lang.Iterable<? extends kotlin.Pair<? extends K, ? extends V>>): java.util.Map<K, V>");
    }

    @Test
    void decodesArgumentsIntoConvertedTypesAndAppliesDefaults() throws Exception {
        JsonRpcEndpoint endpoint = textAndCollectionEndpoint();

        assertThat(endpoint.call("padStart", """
                {"receiver": "42", "length": 5, "padChar": "0"}
                """)).isEqualTo("\"00042\"");
        assertThat(endpoint.call("padStart", """
                {"receiver": "ok", "length": 4}
                """)).isEqualTo("\"  ok\"");

        assertThat(endpoint.call("split", """
                {"receiver": "red; green;blue", "delimiters": ["; ", ";"]}
                """)).isEqualTo("[\"red\",\"green\",\"blue\"]");
        assertThat(endpoint.call("split", """
                {"receiver": "aXbxc", "delimiters": ["x"], "ignoreCase": true, "limit": 2}
                """)).isEqualTo("[\"a\",\"bxc\"]");

        assertThat(endpoint.call("parseInt", """
                {"receiver": "ff", "radix": 16}
                """)).isEqualTo("255");
        assertThat(endpoint.call("parseInt", """
                {"receiver": "zz", "radix": 10}
                """)).isEqualTo("null");

        assertThat(endpoint.call("windowed", """
                {"receiver": [1, 2, 3, 4], "size": 2}
                """)).isEqualTo("[[1,2],[2,3],[3,4]]");
        assertThat(endpoint.call("windowed", """
                {"receiver": [1, 2, 3, 4, 5], "size": 2, "step": 2, "partialWindows": true}
                """)).isEqualTo("[[1,2],[3,4],[5]]");
    }

    @Test
    void decodesKotlinPairsThroughTheKotlinModule() throws Exception {
        JsonRpcEndpoint endpoint = textAndCollectionEndpoint();

        String index = endpoint.call("index", """
                {"receiver": [
                    {"first": "eu-west", "second": 3},
                    {"first": "us-east", "second": 5},
                    {"first": "eu-west", "second": 4}
                ]}
                """);

        assertThat(index).isEqualTo("{\"eu-west\":4,\"us-east\":5}");
    }

    @Test
    void rejectsArgumentsThatDoNotFitTheKotlinSignature() throws NoSuchMethodException {
        JsonRpcEndpoint endpoint = textAndCollectionEndpoint();

        assertThatThrownBy(() -> endpoint.call("padStart", """
                {"receiver": "7"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("padStart: missing argument length");
        assertThatThrownBy(() -> endpoint.call("padStart", """
                {"receiver": null, "length": 3}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("padStart: argument receiver must not be null");
        assertThatThrownBy(() -> endpoint.call("split", """
                {"receiver": "a,b", "delimiters": ","}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("split: argument delimiters must be a JSON array");
        assertThatThrownBy(() -> endpoint.call("windowed", """
                {"receiver": [1, 2], "size": "two"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("windowed: argument size is not a [simple type, class int]");
        assertThatThrownBy(() -> endpoint.describe("reverse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown operation reverse");
    }

    private static <T> KClass<T> kotlin(Class<T> type) {
        return JvmClassMappingKt.getKotlinClass(type);
    }
}
