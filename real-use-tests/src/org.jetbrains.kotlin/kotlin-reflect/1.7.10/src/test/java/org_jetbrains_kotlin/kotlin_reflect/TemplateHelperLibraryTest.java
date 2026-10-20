/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Pair;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.comparisons.ComparisonsKt;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeParameter;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.KTypesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlin.text.StringsKt;
import org.junit.jupiter.api.Test;

/**
 * Top-level function reflection on Kotlin file facades: a template engine publishes stdlib
 * top-level functions as template helpers by mapping the static facade methods to `KFunction`s.
 *
 * Template authors call helpers by name with named arguments, as in
 * `padStart(receiver, length = 5, padChar = '0')`. The engine reads the Kotlin view of each
 * facade method to learn the extension receiver, parameter names, defaults, `vararg`
 * parameters and generic type parameters, resolves overloads from the argument values, and
 * invokes the chosen function through `callBy` so that omitted arguments take their defaults.
 */
class TemplateHelperLibraryTest {

    /** Template helper library backed by top-level functions of Kotlin file facades. */
    static final class HelperLibrary {

        private final Map<String, List<KFunction<?>>> helpers = new LinkedHashMap<>();

        /** Publishes the public top-level functions of `facade` whose names are in `names`. */
        HelperLibrary publish(Class<?> facade, String... names) {
            List<String> wanted = List.of(names);
            for (Method method : facade.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.isSynthetic()
                        || !wanted.contains(method.getName())) {
                    continue;
                }
                KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
                if (function == null) {
                    throw new IllegalStateException(method + " has no Kotlin declaration");
                }
                // Multifile facades delegate to package-private parts, which kotlin-reflect calls.
                KCallablesJvm.setAccessible(function, true);
                helpers.computeIfAbsent(function.getName(), name -> new ArrayList<>()).add(function);
            }
            return this;
        }

        /** Calls the most specific overload of `name` that accepts `receiver` and `arguments`. */
        Object call(String name, Object receiver, Map<String, Object> arguments) {
            KFunction<?> function = resolve(name, receiver, arguments);
            return function.callBy(bind(function, receiver, arguments));
        }

        /** Calls the overload of `name` whose only value parameter is a `vararg`, spreading `values`. */
        Object spread(String name, List<?> values) {
            for (KFunction<?> function : overloads(name)) {
                List<KParameter> parameters = valueParameters(function);
                if (receiverOf(function) == null && parameters.size() == 1 && parameters.get(0).isVararg()) {
                    KParameter vararg = parameters.get(0);
                    Object array = Array.newInstance(
                            erasure(vararg.getType()).getComponentType(), values.size());
                    for (int i = 0; i < values.size(); i++) {
                        Array.set(array, i, values.get(i));
                    }
                    return function.call(array);
                }
            }
            throw new IllegalArgumentException(name + " has no vararg overload");
        }

        /** The overload `call` would pick, exposed for the engine's help page. */
        KFunction<?> resolve(String name, Object receiver, Map<String, Object> arguments) {
            KFunction<?> best = null;
            for (KFunction<?> function : overloads(name)) {
                if (accepts(function, receiver, arguments) && (best == null || moreSpecific(function, best))) {
                    best = function;
                }
            }
            if (best == null) {
                throw new IllegalArgumentException("no overload of " + name + " accepts " + arguments.keySet());
            }
            return best;
        }

        /** Every overload of `name`, in the order the facade declares them. */
        List<KFunction<?>> overloads(String name) {
            List<KFunction<?>> overloads = helpers.get(name);
            if (overloads == null) {
                throw new IllegalArgumentException("unknown helper " + name);
            }
            return overloads;
        }

        /** The help line for `function`, written as its Kotlin declaration. */
        static String signature(KFunction<?> function) {
            StringBuilder line = new StringBuilder("fun ");
            List<KTypeParameter> typeParameters = function.getTypeParameters();
            if (!typeParameters.isEmpty()) {
                List<String> rendered = new ArrayList<>();
                for (KTypeParameter typeParameter : typeParameters) {
                    String bound = "";
                    for (KType upperBound : typeParameter.getUpperBounds()) {
                        if (!render(upperBound).equals("Any?")) {
                            bound = " : " + render(upperBound);
                        }
                    }
                    rendered.add(typeParameter.getName() + bound);
                }
                line.append('<').append(String.join(", ", rendered)).append("> ");
            }
            KParameter receiver = receiverOf(function);
            if (receiver != null) {
                line.append(render(receiver.getType())).append('.');
            }
            List<String> parameters = new ArrayList<>();
            for (KParameter parameter : valueParameters(function)) {
                KType type = parameter.getType();
                String rendered = parameter.isVararg()
                        ? "vararg " + parameter.getName() + ": " + render(type.getArguments().get(0).getType())
                        : parameter.getName() + ": " + render(type);
                parameters.add(rendered + (parameter.isOptional() ? " = ..." : ""));
            }
            return line.append(function.getName()).append('(').append(String.join(", ", parameters))
                    .append("): ").append(render(function.getReturnType())).toString();
        }

        private static boolean accepts(KFunction<?> function, Object receiver, Map<String, Object> arguments) {
            KParameter receiverParameter = receiverOf(function);
            if ((receiverParameter == null) != (receiver == null)
                    || receiverParameter != null && !fits(receiverParameter, receiver)) {
                return false;
            }
            List<KParameter> parameters = valueParameters(function);
            int matched = 0;
            for (KParameter parameter : parameters) {
                if (arguments.containsKey(parameter.getName())) {
                    if (parameter.isVararg() || !fits(parameter, arguments.get(parameter.getName()))) {
                        return false;
                    }
                    matched++;
                } else if (!parameter.isOptional()) {
                    return false;
                }
            }
            return matched == arguments.size();
        }

        /** An overload is more specific when each of its parameters is a subtype of the other's. */
        private static boolean moreSpecific(KFunction<?> candidate, KFunction<?> current) {
            List<KParameter> left = candidate.getParameters();
            List<KParameter> right = current.getParameters();
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (!KClasses.isSubclassOf(KTypesJvm.getJvmErasure(left.get(i).getType()),
                        KTypesJvm.getJvmErasure(right.get(i).getType()))) {
                    return false;
                }
            }
            return true;
        }

        private static Map<KParameter, Object> bind(KFunction<?> function, Object receiver,
                Map<String, Object> arguments) {
            Map<KParameter, Object> bound = new HashMap<>();
            for (KParameter parameter : function.getParameters()) {
                if (parameter.getKind() == KParameter.Kind.EXTENSION_RECEIVER) {
                    bound.put(parameter, receiver);
                } else if (arguments.containsKey(parameter.getName())) {
                    bound.put(parameter, arguments.get(parameter.getName()));
                }
            }
            return bound;
        }

        private static boolean fits(KParameter parameter, Object value) {
            return value == null ? parameter.getType().isMarkedNullable()
                    : KTypesJvm.getJvmErasure(parameter.getType()).isInstance(value);
        }

        private static Class<?> erasure(KType type) {
            return JvmClassMappingKt.getJavaClass(KTypesJvm.getJvmErasure(type));
        }

        private static KParameter receiverOf(KFunction<?> function) {
            for (KParameter parameter : function.getParameters()) {
                if (parameter.getKind() == KParameter.Kind.EXTENSION_RECEIVER) {
                    return parameter;
                }
            }
            return null;
        }

        private static List<KParameter> valueParameters(KFunction<?> function) {
            List<KParameter> values = new ArrayList<>();
            for (KParameter parameter : function.getParameters()) {
                if (parameter.getKind() == KParameter.Kind.VALUE) {
                    values.add(parameter);
                }
            }
            return values;
        }

        private static String render(KType type) {
            KClassifier classifier = type.getClassifier();
            String name = classifier instanceof KTypeParameter
                    ? ((KTypeParameter) classifier).getName()
                    : ((KClass<?>) classifier).getSimpleName();
            List<String> arguments = new ArrayList<>();
            for (KTypeProjection argument : type.getArguments()) {
                arguments.add(argument.getType() == null ? "*" : render(argument.getType()));
            }
            return name + (arguments.isEmpty() ? "" : "<" + String.join(", ", arguments) + ">")
                    + (type.isMarkedNullable() ? "?" : "");
        }
    }

    private static HelperLibrary stdlibHelpers() {
        return new HelperLibrary()
                .publish(StringsKt.class, "padStart", "substringBefore")
                .publish(CollectionsKt.class, "joinToString", "listOf")
                .publish(MapsKt.class, "mapOf")
                .publish(ComparisonsKt.class, "maxOf");
    }

    @Test
    void rendersHelpPageFromFacadeDeclarations() {
        HelperLibrary helpers = stdlibHelpers();

        assertThat(HelperLibrary.signature(helpers.resolve("padStart", "7", Map.of("length", 3))))
                .isEqualTo("fun String.padStart(length: Int, padChar: Char = ...): String");
        assertThat(HelperLibrary.signature(helpers.resolve("substringBefore", "a=b", Map.of("delimiter", "="))))
                .isEqualTo("fun String.substringBefore(delimiter: String, "
                        + "missingDelimiterValue: String = ...): String");
        assertThat(HelperLibrary.signature(helpers.resolve("maxOf", null, Map.of("a", "x", "b", "y"))))
                .isEqualTo("fun <T : Comparable<T>> maxOf(a: T, b: T): T");

        List<String> varargSignatures = new ArrayList<>();
        for (String name : List.of("listOf", "mapOf")) {
            for (KFunction<?> function : helpers.overloads(name)) {
                if (!function.getParameters().isEmpty() && function.getParameters().get(0).isVararg()) {
                    varargSignatures.add(HelperLibrary.signature(function));
                }
            }
        }
        assertThat(varargSignatures).containsExactly(
                "fun <T> listOf(vararg elements: T): List<T>",
                "fun <K, V> mapOf(vararg pairs: Pair<K, V>): Map<K, V>");
    }

    @Test
    void callsExtensionHelpersWithNamedArgumentsAndDefaults() {
        HelperLibrary helpers = stdlibHelpers();

        assertThat(helpers.call("padStart", "42", Map.of("length", 5))).isEqualTo("   42");
        assertThat(helpers.call("padStart", "42", Map.of("length", 5, "padChar", '0'))).isEqualTo("00042");
        assertThat(helpers.call("substringBefore", "region=eu-west", Map.of("delimiter", "=")))
                .isEqualTo("region");
        assertThat(helpers.call("substringBefore", "plain", Map.of("delimiter", "="))).isEqualTo("plain");
        assertThat(helpers.call("substringBefore", "plain",
                Map.of("delimiter", "=", "missingDelimiterValue", "none"))).isEqualTo("none");

        List<String> tags = List.of("kotlin", "reflect", "native", "image");
        assertThat(helpers.call("joinToString", tags, Map.of())).isEqualTo("kotlin, reflect, native, image");
        assertThat(helpers.call("joinToString", tags,
                Map.of("separator", " | ", "prefix", "[", "postfix", "]")))
                .isEqualTo("[kotlin | reflect | native | image]");
        assertThat(helpers.call("joinToString", tags, Map.of("limit", 2, "truncated", "more")))
                .isEqualTo("kotlin, reflect, more");
    }

    @Test
    void resolvesOverloadsFromArgumentValues() {
        HelperLibrary helpers = stdlibHelpers();

        KFunction<?> generic = helpers.resolve("maxOf", null, Map.of("a", "pear", "b", "apple"));
        assertThat(generic.getTypeParameters()).hasSize(1);
        assertThat(helpers.call("maxOf", null, Map.of("a", "pear", "b", "apple"))).isEqualTo("pear");

        // `maxOf(Int, Int)` is inline-only, so integers resolve to the `Comparable` overload too.
        assertThat(helpers.resolve("maxOf", null, Map.of("a", 3, "b", 11))).isEqualTo(generic);
        assertThat(helpers.call("maxOf", null, Map.of("a", 3, "b", 11))).isEqualTo(11);

        KFunction<?> ternary = helpers.resolve("maxOf", null, Map.of("a", 2L, "b", 9L, "c", 5L));
        assertThat(HelperLibrary.signature(ternary)).isEqualTo("fun <T : Comparable<T>> maxOf(a: T, b: T, c: T): T");
        assertThat(helpers.call("maxOf", null, Map.of("a", 2L, "b", 9L, "c", 5L))).isEqualTo(9L);

        assertThatThrownBy(() -> helpers.call("padStart", "42", Map.of("padChar", '0')))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no overload of padStart accepts [padChar]");
        assertThatThrownBy(() -> helpers.call("trimEnd", "x ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown helper trimEnd");
    }

    @Test
    void spreadsValuesIntoVarargHelpers() {
        HelperLibrary helpers = stdlibHelpers();

        Object regions = helpers.spread("listOf", List.of("eu-west", "us-east", "ap-south"));
        assertThat(regions).isInstanceOf(List.class);
        assertThat(regions).isEqualTo(List.of("eu-west", "us-east", "ap-south"));

        Object limits = helpers.spread("mapOf", List.of(new Pair<>("cpu", 4), new Pair<>("memory", 512)));
        assertThat(limits).isEqualTo(Map.of("cpu", 4, "memory", 512));
        assertThat(new ArrayList<Object>(((Map<?, ?>) limits).keySet())).isEqualTo(List.of("cpu", "memory"));

        assertThatThrownBy(() -> helpers.spread("padStart", List.of("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("padStart has no vararg overload");
    }
}
