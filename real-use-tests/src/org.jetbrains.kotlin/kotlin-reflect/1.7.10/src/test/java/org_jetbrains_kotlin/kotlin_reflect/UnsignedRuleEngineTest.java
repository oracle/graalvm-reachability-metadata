/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.UByte;
import kotlin.UInt;
import kotlin.ULong;
import kotlin.UNumbersKt;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.URangesKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.KTypesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlin.text.UStringsKt;
import org.junit.jupiter.api.Test;

/**
 * Value-class calling convention: a packet-inspection rule engine evaluates unsigned arithmetic
 * on Kotlin's `UInt`, `ULong` and `UByte` by calling their members and stdlib extensions
 * through `KFunction.call`.
 *
 * Rules are pipelines such as `0x811C9DC5u |> xor 97u |> times 16777619u |> toString 16`. On
 * the JVM these operations are static methods with mangled names that take and return raw
 * `int`, `long` and `byte` values, and most of them are private `@InlineOnly` bodies. The
 * engine never sees that: it works on the Kotlin model, where each operation has its source
 * name and value-class parameter types, and kotlin-reflect unboxes the receiver and arguments
 * and boxes the result, so wrap-around and unsigned comparison behave as in Kotlin code.
 */
class UnsignedRuleEngineTest {

    /** Rule engine that dispatches pipeline steps to Kotlin functions of the receiver's type. */
    static final class RuleEngine {

        private final Map<Class<?>, Map<String, List<KFunction<?>>>> operations = new HashMap<>();

        /** Publishes the member functions of the value class `type`. */
        RuleEngine members(Class<?> type) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            if (!kClass.isValue()) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " is not a value class");
            }
            for (KFunction<?> function : KClasses.getMemberFunctions(kClass)) {
                publish(kClass, function);
            }
            return this;
        }

        /** Publishes the extension functions declared in the Kotlin file facade `facade`. */
        RuleEngine extensions(Class<?> facade) {
            // Inline-only extensions are private, and multifile parts are inherited by the facade.
            Set<Method> methods = new LinkedHashSet<>(List.of(facade.getDeclaredMethods()));
            methods.addAll(List.of(facade.getMethods()));
            for (Method method : methods) {
                if (!Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
                if (function == null) {
                    continue; // property accessors and Object methods have no Kotlin function
                }
                KParameter receiver = KCallables.getExtensionReceiverParameter(function);
                if (receiver != null) {
                    publish(KTypesJvm.getJvmErasure(receiver.getType()), function);
                }
            }
            return this;
        }

        private void publish(KClass<?> receiver, KFunction<?> function) {
            KCallablesJvm.setAccessible(function, true);
            operations.computeIfAbsent(JvmClassMappingKt.getJavaObjectType(receiver), key -> new HashMap<>())
                    .computeIfAbsent(function.getName(), key -> new ArrayList<>())
                    .add(function);
        }

        /** Evaluates a `|>`-separated pipeline whose first stage is a literal. */
        Object evaluate(String rule) {
            String[] stages = rule.split("\\|>");
            Object value = literal(stages[0].trim());
            for (int i = 1; i < stages.length; i++) {
                String[] tokens = stages[i].trim().split("\\s+");
                List<Object> arguments = new ArrayList<>();
                for (int t = 1; t < tokens.length; t++) {
                    arguments.add(literal(tokens[t]));
                }
                if (value == null) {
                    throw new IllegalStateException("stage '" + stages[i].trim() + "' has a null receiver");
                }
                value = apply(value, tokens[0], arguments);
            }
            return value;
        }

        /** Parses `"text"`, `42`, `42u`, `42uL` and their `0x` forms through the stdlib parsers. */
        Object literal(String token) {
            if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
                return token.substring(1, token.length() - 1);
            }
            String parser;
            String digits;
            if (token.endsWith("uL")) {
                parser = "toULong";
                digits = token.substring(0, token.length() - 2);
            } else if (token.endsWith("u")) {
                parser = "toUInt";
                digits = token.substring(0, token.length() - 1);
            } else {
                return Integer.decode(token);
            }
            boolean hex = digits.startsWith("0x");
            return apply(hex ? digits.substring(2) : digits, parser, List.of(hex ? 16 : 10));
        }

        /** Calls the operation `name` of `receiver` whose value parameters accept `arguments`. */
        Object apply(Object receiver, String name, List<Object> arguments) {
            KFunction<?> function = resolve(receiver, name, arguments);
            Object[] callArguments = new Object[arguments.size() + 1];
            callArguments[0] = receiver;
            for (int i = 0; i < arguments.size(); i++) {
                callArguments[i + 1] = arguments.get(i);
            }
            try {
                return function.call(callArguments);
            } catch (Exception failure) {
                // kotlin-reflect lets the operation's own exception surface wrapped by the JVM call.
                if (failure instanceof InvocationTargetException invocation
                        && invocation.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                throw failure;
            }
        }

        /** Finds the single overload of `name` on the receiver's Kotlin class for `arguments`. */
        KFunction<?> resolve(Object receiver, String name, List<Object> arguments) {
            KClass<?> type = JvmClassMappingKt.getKotlinClass(receiver.getClass());
            List<KFunction<?>> candidates = operations.getOrDefault(receiver.getClass(), Map.of())
                    .getOrDefault(name, List.of());
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(type.getSimpleName() + " has no operation '" + name + "'");
            }
            List<KFunction<?>> matches = new ArrayList<>();
            for (KFunction<?> candidate : candidates) {
                if (accepts(candidate, arguments)) {
                    matches.add(candidate);
                }
            }
            if (matches.size() != 1) {
                throw new IllegalArgumentException(type.getSimpleName() + "." + name + " has "
                        + matches.size() + " overloads for " + typeNames(arguments));
            }
            return matches.get(0);
        }

        private static boolean accepts(KFunction<?> function, List<Object> arguments) {
            List<KParameter> parameters = KCallables.getValueParameters(function);
            if (parameters.size() != arguments.size()) {
                return false;
            }
            for (int i = 0; i < parameters.size(); i++) {
                if (!KTypesJvm.getJvmErasure(parameters.get(i).getType()).isInstance(arguments.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private static List<String> typeNames(List<Object> arguments) {
            List<String> names = new ArrayList<>();
            for (Object argument : arguments) {
                names.add(JvmClassMappingKt.getKotlinClass(argument.getClass()).getSimpleName());
            }
            return names;
        }
    }

    private static RuleEngine engine() {
        return new RuleEngine()
                .members(UInt.class)
                .members(ULong.class)
                .members(UByte.class)
                .extensions(UStringsKt.class)
                .extensions(UNumbersKt.class)
                .extensions(URangesKt.class);
    }

    @Test
    void parsesUnsignedLiteralsIntoValueClassBoxes() {
        RuleEngine engine = engine();

        Object max = engine.literal("4294967295u");
        assertThat(max).isInstanceOf(UInt.class);
        assertThat(max).hasToString("4294967295");
        assertThat(engine.literal("0xFFFFFFFFu")).isEqualTo(max);

        Object longMax = engine.literal("18446744073709551615uL");
        assertThat(longMax).isInstanceOf(ULong.class);
        assertThat(longMax).hasToString("18446744073709551615");

        assertThat(engine.literal("0x10")).isEqualTo(16);
        assertThat(engine.literal("\"300\"")).isEqualTo("300");
    }

    @Test
    void wrapsAndComparesWithUnsignedSemantics() {
        RuleEngine engine = engine();

        Object wrapped = engine.evaluate("4294967295u |> plus 1u");
        assertThat(wrapped).isInstanceOf(UInt.class);
        assertThat(wrapped).hasToString("0");
        assertThat(engine.evaluate("0u |> minus 1u")).hasToString("4294967295");
        assertThat(engine.evaluate("0xFFFFFFFFu |> div 2u")).hasToString("2147483647");
        assertThat(engine.evaluate("0xFFFFFFFFu |> rem 10u")).hasToString("5");

        // The same 32 bits are -1 as an Int, yet the UInt compares above 1.
        assertThat((Integer) engine.evaluate("0xFFFFFFFFu |> compareTo 1u")).isPositive();
        assertThat((Integer) engine.evaluate("0xFFFFFFFFu |> toInt")).isEqualTo(-1);
    }

    @Test
    void computesAnFnvChecksumStepWithBitOperations() {
        RuleEngine engine = engine();

        // One FNV-1a round over the byte 'a' (97) from the 32-bit offset basis.
        assertThat(engine.evaluate("0x811C9DC5u |> xor 97u |> times 16777619u |> toString 16"))
                .isEqualTo("e40c292c");
        assertThat(engine.evaluate("0x80000001u |> rotateLeft 1 |> toString 16")).isEqualTo("3");
        assertThat(engine.evaluate("0xF0F0u |> countOneBits")).isEqualTo(8);
        assertThat(engine.evaluate("0xF0F0u |> takeHighestOneBit |> toString 2"))
                .isEqualTo("1000000000000000");
        assertThat(engine.evaluate("0xFFu |> and 0x0Fu |> or 0x100u |> toString 16")).isEqualTo("10f");
    }

    @Test
    void resolvesOverloadsAcrossUnsignedWidths() {
        RuleEngine engine = engine();

        Object fromByte = engine.evaluate("255u |> toUByte");
        assertThat(fromByte).isInstanceOf(UByte.class);
        assertThat(engine.evaluate("255u |> toUByte |> toInt")).isEqualTo(255);
        // UByte + UInt widens to UInt instead of wrapping at 8 bits.
        Object widened = engine.evaluate("255u |> toUByte |> plus 1u");
        assertThat(widened).isInstanceOf(UInt.class);
        assertThat(widened).hasToString("256");

        Object longWrapped = engine.evaluate("18446744073709551615uL |> plus 1u");
        assertThat(longWrapped).isInstanceOf(ULong.class);
        assertThat(longWrapped).hasToString("0");
        assertThat(engine.evaluate("4294967295u |> plus 1uL")).isInstanceOf(ULong.class)
                .hasToString("4294967296");
    }

    @Test
    void clampsAndParsesWithNullableValueClassResults() {
        RuleEngine engine = engine();

        assertThat(engine.evaluate("300u |> coerceIn 0u 255u")).hasToString("255");
        assertThat(engine.evaluate("7u |> coerceIn 10u 20u")).hasToString("10");

        Object parsed = engine.evaluate("\"4294967295\" |> toUIntOrNull");
        assertThat(parsed).isInstanceOf(UInt.class);
        assertThat(parsed).hasToString("4294967295");
        assertThat(engine.evaluate("\"4294967296\" |> toUIntOrNull")).isNull();
        assertThat(engine.evaluate("\"-1\" |> toUIntOrNull")).isNull();
        assertThat(engine.evaluate("\"ff\" |> toUIntOrNull 16")).hasToString("255");
    }

    @Test
    void exposesKotlinSignaturesOverMangledJvmMethods() {
        RuleEngine engine = engine();
        Object one = engine.literal("1u");
        KClass<UInt> uint = JvmClassMappingKt.getKotlinClass(UInt.class);

        KFunction<?> plus = engine.resolve(one, "plus", List.of(one));
        assertThat(plus.getName()).isEqualTo("plus");
        assertThat(KCallables.getInstanceParameter(plus).getType().getClassifier()).isEqualTo(uint);
        assertThat(KCallables.getValueParameters(plus).get(0).getType().getClassifier()).isEqualTo(uint);
        assertThat(plus.getReturnType().getClassifier()).isEqualTo(uint);
        assertThat(plus.isOperator()).isTrue();

        Method jvmPlus = ReflectJvmMapping.getJavaMethod(plus);
        assertThat(jvmPlus).isNotNull();
        assertThat(jvmPlus.getName()).startsWith("plus-");
        assertThat(jvmPlus.getReturnType()).isEqualTo(int.class);

        KFunction<?> render = engine.resolve(one, "toString", List.of(16));
        assertThat(KCallables.getExtensionReceiverParameter(render).getType().getClassifier()).isEqualTo(uint);
        Method jvmRender = ReflectJvmMapping.getJavaMethod(render);
        assertThat(jvmRender.getDeclaringClass()).isEqualTo(UStringsKt.class);
        assertThat(jvmRender.getName()).startsWith("toString-");
        assertThat(jvmRender.getParameterTypes()).containsExactly(int.class, int.class);
    }

    @Test
    void reportsRuleErrors() {
        RuleEngine engine = engine();

        assertThatThrownBy(() -> engine.evaluate("1u |> div 0u"))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> engine.evaluate("1u |> checksum"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UInt has no operation 'checksum'");
        assertThatThrownBy(() -> engine.evaluate("1u |> plus 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UInt.plus has 0 overloads for [Int]");
        assertThatThrownBy(() -> engine.evaluate("\"x\" |> toUIntOrNull |> plus 1u"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stage 'plus 1u' has a null receiver");
    }
}
