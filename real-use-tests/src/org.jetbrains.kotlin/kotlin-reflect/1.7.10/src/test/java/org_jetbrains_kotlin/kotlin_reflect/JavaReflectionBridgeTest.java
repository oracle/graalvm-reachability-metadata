/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Pair;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.IntProgression;
import kotlin.ranges.IntRange;
import kotlin.reflect.KClass;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KType;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlin.text.MatchResult;
import kotlin.text.Regex;
import org.junit.jupiter.api.Test;

/**
 * Java-to-Kotlin reflection bridging with `ReflectJvmMapping`: a Java command framework scans
 * handler classes with `java.lang.reflect` and maps each `Method`, `Constructor` and `Field` to
 * its Kotlin declaration to recover parameter names, defaults and nullability.
 *
 * The framework discovers handlers the way any Java container does, by `Class.getMethods()`.
 * Only the Kotlin view knows that `startIndex` of `Regex.find` has a default and that the result
 * is nullable, so the framework bridges to `KFunction`, invokes through `callBy` with the
 * defaults omitted, and maps Kotlin properties back to the Java getter and backing field it uses
 * for fast access.
 */
class JavaReflectionBridgeTest {

    /** Command framework that exposes the public Kotlin members of a handler class by name. */
    static final class CommandFramework {

        private final Map<String, KFunction<?>> commands = new LinkedHashMap<>();

        /** Registers every public method of `handler` that has a Kotlin declaration. */
        CommandFramework(Class<?> handler, List<String> exposed) {
            for (Method method : handler.getMethods()) {
                if (method.isSynthetic() || !exposed.contains(method.getName())) {
                    continue;
                }
                KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
                if (function == null) {
                    throw new IllegalStateException(method + " has no Kotlin declaration");
                }
                commands.merge(function.getName(), function, CommandFramework::widest);
            }
        }

        /** Keeps the overload with the most parameters, so defaults decide what can be omitted. */
        private static KFunction<?> widest(KFunction<?> first, KFunction<?> second) {
            return second.getParameters().size() > first.getParameters().size() ? second : first;
        }

        /** The signature the framework prints in its help output, in Kotlin terms. */
        String signature(String command) {
            KFunction<?> function = command(command);
            List<String> parameters = new ArrayList<>();
            for (KParameter parameter : function.getParameters()) {
                if (parameter.getKind() == KParameter.Kind.VALUE) {
                    parameters.add(parameter.getName() + ": " + typeName(parameter.getType())
                            + (parameter.isOptional() ? " = default" : ""));
                }
            }
            return function.getName() + "(" + String.join(", ", parameters) + "): "
                    + typeName(function.getReturnType());
        }

        /** Runs `command` on `target` with the named arguments; omitted optional ones take defaults. */
        Object run(String command, Object target, Map<String, Object> arguments) {
            KFunction<?> function = command(command);
            Map<KParameter, Object> bound = new HashMap<>();
            for (KParameter parameter : function.getParameters()) {
                if (parameter.getKind() == KParameter.Kind.INSTANCE) {
                    bound.put(parameter, target);
                } else if (arguments.containsKey(parameter.getName())) {
                    bound.put(parameter, arguments.get(parameter.getName()));
                } else if (!parameter.isOptional()) {
                    throw new IllegalArgumentException(command + " requires argument " + parameter.getName());
                }
            }
            return function.callBy(bound);
        }

        /** The Java method the framework caches for repeated invocations of `command`. */
        Method javaMethod(String command) {
            return ReflectJvmMapping.getJavaMethod(command(command));
        }

        KFunction<?> command(String command) {
            KFunction<?> function = commands.get(command);
            if (function == null) {
                throw new IllegalArgumentException("unknown command " + command);
            }
            return function;
        }

        private static String typeName(KType type) {
            KClassifier classifier = type.getClassifier();
            return ((KClass<?>) classifier).getSimpleName() + (type.isMarkedNullable() ? "?" : "");
        }
    }

    @Test
    void bridgesScannedMethodsToKotlinSignatures() {
        CommandFramework framework = new CommandFramework(Regex.class, List.of("find", "matches", "split"));

        assertThat(framework.signature("find"))
                .isEqualTo("find(input: CharSequence, startIndex: Int = default): MatchResult?");
        assertThat(framework.signature("matches")).isEqualTo("matches(input: CharSequence): Boolean");
        assertThat(framework.signature("split"))
                .isEqualTo("split(input: CharSequence, limit: Int = default): List");

        KFunction<?> find = framework.command("find");
        assertThat(ReflectJvmMapping.getJavaType(find.getReturnType())).isEqualTo(MatchResult.class);
        assertThat(ReflectJvmMapping.getJavaType(find.getParameters().get(1).getType()))
                .isEqualTo(CharSequence.class);
    }

    @Test
    void invokesThroughKotlinDefaultsAndNullableResults() throws ReflectiveOperationException {
        CommandFramework framework = new CommandFramework(Regex.class, List.of("find", "split"));
        Regex orderId = new Regex("ORD-\\d+");

        MatchResult first = (MatchResult) framework.run("find", orderId, Map.of("input", "ORD-17, ORD-42"));
        assertThat(first.getValue()).isEqualTo("ORD-17");

        MatchResult second = (MatchResult) framework.run("find", orderId,
                Map.of("input", "ORD-17, ORD-42", "startIndex", 3));
        assertThat(second.getValue()).isEqualTo("ORD-42");

        assertThat(framework.run("find", orderId, Map.of("input", "no orders"))).isNull();

        Regex comma = new Regex(",\\s*");
        assertThat(framework.run("split", comma, Map.of("input", "a, b, c"))).isEqualTo(List.of("a", "b", "c"));
        assertThat(framework.run("split", comma, Map.of("input", "a, b, c", "limit", 2)))
                .isEqualTo(List.of("a", "b, c"));

        Method cached = framework.javaMethod("find");
        assertThat(cached).isEqualTo(Regex.class.getMethod("find", CharSequence.class, int.class));
        assertThat(((MatchResult) cached.invoke(orderId, "ORD-99", 0)).getValue()).isEqualTo("ORD-99");
    }

    @Test
    void rejectsMissingRequiredArgumentsAndUnknownCommands() {
        CommandFramework framework = new CommandFramework(Regex.class, List.of("find"));

        assertThatThrownBy(() -> framework.run("find", new Regex("x"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("find requires argument input");
        assertThatThrownBy(() -> framework.signature("replace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown command replace");
    }

    @Test
    void bridgesConstructorsInBothDirections() throws ReflectiveOperationException {
        Constructor<Pair> javaConstructor = Pair.class.getConstructor(Object.class, Object.class);
        KFunction<Pair> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(javaConstructor);

        assertThat(kotlinConstructor).isEqualTo(KClasses.getPrimaryConstructor(JvmClassMappingKt.getKotlinClass(Pair.class)));
        List<String> names = new ArrayList<>();
        for (KParameter parameter : kotlinConstructor.getParameters()) {
            names.add(parameter.getName());
        }
        assertThat(names).containsExactly("first", "second");
        assertThat(kotlinConstructor.call("gate", 7)).isEqualTo(new Pair<>("gate", 7));
        assertThat(ReflectJvmMapping.getJavaConstructor(kotlinConstructor)).isEqualTo(javaConstructor);
    }

    @Test
    void mapsPropertiesToGettersAndBackingFields() throws ReflectiveOperationException {
        KClass<IntRange> range = JvmClassMappingKt.getKotlinClass(IntRange.class);
        Map<String, KProperty1<IntRange, ?>> properties = new HashMap<>();
        for (KProperty1<IntRange, ?> property : KClasses.getMemberProperties(range)) {
            properties.put(property.getName(), property);
        }

        KProperty1<IntRange, ?> first = properties.get("first");
        Method getter = ReflectJvmMapping.getJavaGetter(first);
        assertThat(getter.getName()).isEqualTo("getFirst");
        Field field = ReflectJvmMapping.getJavaField(first);
        assertThat(field.getName()).isEqualTo("first");
        assertThat(field.getDeclaringClass()).isEqualTo(IntProgression.class);

        KProperty<?> fromField = ReflectJvmMapping.getKotlinProperty(field);
        assertThat(fromField.getName()).isEqualTo("first");

        KProperty1<IntRange, ?> start = properties.get("start");
        assertThat(ReflectJvmMapping.getJavaField(start)).isNull();
        assertThat(ReflectJvmMapping.getJavaGetter(start).getName()).isEqualTo("getStart");

        IntRange window = new IntRange(10, 20);
        assertThat(getter.invoke(window)).isEqualTo(10);
        assertThat(start.get(window)).isEqualTo(10);
        assertThat(properties.get("last").get(window)).isEqualTo(20);
    }
}
