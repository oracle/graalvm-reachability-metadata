/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlin.collections.ArrayDeque;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.CharRange;
import kotlin.ranges.ClosedRange;
import kotlin.ranges.IntRange;
import kotlin.reflect.KClass;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.full.KClassifiers;
import kotlin.reflect.full.KTypes;
import org.junit.jupiter.api.Test;

/**
 * Kotlin type algebra: a service registry binds components under full generic `KType`s built
 * with `createType` and resolves a request to the binding whose type `isSubtypeOf` it.
 *
 * The registry never compares classes. A request for `Iterable<Int>` matches an `IntRange` but not
 * a `CharRange`, variance in a projection widens or narrows the match, a nullable request accepts
 * a non-null binding, and a nullable binding never satisfies a non-null request.
 */
class TypedServiceRegistryTest {

    /** Registry of application components keyed by their declared Kotlin type. */
    static final class TypedServiceRegistry {

        private final List<Binding> bindings = new ArrayList<>();

        /** One component and the type it was registered under. */
        record Binding(KType type, Object component) {
        }

        void bind(KType type, Object component) {
            bindings.add(new Binding(type, component));
        }

        /** Every binding whose type is a subtype of `requested`, in registration order. */
        List<Binding> candidates(KType requested) {
            List<Binding> matches = new ArrayList<>();
            for (Binding binding : bindings) {
                if (KTypes.isSubtypeOf(binding.type(), requested)) {
                    matches.add(binding);
                }
            }
            return matches;
        }

        /** The single component satisfying `requested`; fails on a missing or ambiguous binding. */
        Object resolve(KType requested) {
            List<Binding> matches = candidates(requested);
            if (matches.isEmpty()) {
                throw new IllegalStateException("no binding for " + requested);
            }
            if (matches.size() > 1) {
                List<String> types = new ArrayList<>();
                for (Binding match : matches) {
                    types.add(match.type().toString());
                }
                throw new IllegalStateException("ambiguous binding for " + requested + ": " + types);
            }
            return matches.get(0).component();
        }
    }

    private static KType type(Class<?> type, KTypeProjection... arguments) {
        KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
        return KClassifiers.createType(kClass, List.of(arguments), false, Collections.emptyList());
    }

    private static KTypeProjection of(KType type) {
        return KTypeProjection.invariant(type);
    }

    private static final KType INT = type(Integer.class);
    private static final KType CHAR = type(Character.class);
    private static final KType STRING = type(String.class);

    private static TypedServiceRegistry populatedRegistry(
            IntRange portRange, CharRange shardKeys, ArrayDeque<String> jobQueue) {
        TypedServiceRegistry registry = new TypedServiceRegistry();
        registry.bind(type(IntRange.class), portRange);
        registry.bind(type(CharRange.class), shardKeys);
        registry.bind(type(ArrayDeque.class, of(STRING)), jobQueue);
        registry.bind(STRING, "eu-west-1");
        return registry;
    }

    @Test
    void buildsReadableGenericTypes() {
        KType queue = type(ArrayDeque.class, of(STRING));
        KType ranges = type(List.class, KTypeProjection.covariant(type(ClosedRange.class, of(INT))));
        KType sink = type(Comparable.class, KTypeProjection.contravariant(STRING));

        assertThat(queue).hasToString("kotlin.collections.ArrayDeque<kotlin.String>");
        assertThat(ranges).hasToString("kotlin.collections.List<out kotlin.ranges.ClosedRange<kotlin.Int>>");
        assertThat(sink).hasToString("kotlin.Comparable<in kotlin.String>");
        assertThat(KTypes.withNullability(queue, true)).hasToString("kotlin.collections.ArrayDeque<kotlin.String>?");
        assertThat(KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Iterable.class)))
                .hasToString("kotlin.collections.Iterable<*>");
        assertThat(queue.getArguments()).containsExactly(KTypeProjection.invariant(STRING));
        assertThat(queue).isEqualTo(type(ArrayDeque.class, of(STRING)));
    }

    @Test
    void resolvesByGenericSupertype() {
        IntRange portRange = new IntRange(8_080, 8_089);
        CharRange shardKeys = new CharRange('a', 'f');
        ArrayDeque<String> jobQueue = new ArrayDeque<>(List.of("reindex", "compact"));
        TypedServiceRegistry registry = populatedRegistry(portRange, shardKeys, jobQueue);

        assertThat(registry.resolve(type(Iterable.class, of(INT)))).isSameAs(portRange);
        assertThat(registry.resolve(type(ClosedRange.class, of(CHAR)))).isSameAs(shardKeys);
        assertThat(registry.resolve(type(List.class, of(STRING)))).isSameAs(jobQueue);
        assertThat(registry.resolve(type(CharSequence.class))).isEqualTo("eu-west-1");
        assertThat(registry.resolve(type(Comparable.class, KTypeProjection.contravariant(STRING))))
                .isEqualTo("eu-west-1");
        assertThat(registry.resolve(KTypes.withNullability(type(IntRange.class), true))).isSameAs(portRange);
    }

    @Test
    void varianceAndStarProjectionsWidenTheMatch() {
        IntRange portRange = new IntRange(1, 3);
        CharRange shardKeys = new CharRange('x', 'z');
        ArrayDeque<String> jobQueue = new ArrayDeque<>();
        TypedServiceRegistry registry = populatedRegistry(portRange, shardKeys, jobQueue);

        KType anyIterable = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Iterable.class));
        assertThat(registry.candidates(anyIterable))
                .extracting(TypedServiceRegistry.Binding::component)
                .containsExactly(portRange, shardKeys, jobQueue);

        KType comparableRanges = type(ClosedRange.class,
                KTypeProjection.covariant(type(Comparable.class, KTypeProjection.star)));
        assertThat(registry.candidates(comparableRanges))
                .extracting(TypedServiceRegistry.Binding::component)
                .containsExactly(portRange, shardKeys);

        // `Iterable<out T>` is covariant where it is declared; `ArrayDeque<E>` only at the use site.
        assertThat(registry.candidates(type(Iterable.class, of(type(Number.class)))))
                .extracting(TypedServiceRegistry.Binding::component)
                .containsExactly(portRange);
        KType charSequence = type(CharSequence.class);
        assertThat(registry.candidates(type(ArrayDeque.class, of(charSequence)))).isEmpty();
        assertThat(registry.candidates(type(ArrayDeque.class, KTypeProjection.covariant(charSequence))))
                .extracting(TypedServiceRegistry.Binding::component)
                .containsExactly(jobQueue);
    }

    @Test
    void reportsSupertypeChainOfABoundComponent() {
        KClass<IntRange> intRange = JvmClassMappingKt.getKotlinClass(IntRange.class);
        List<String> supertypes = new ArrayList<>();
        for (KType supertype : KClasses.getAllSupertypes(intRange)) {
            supertypes.add(supertype.toString());
        }

        assertThat(supertypes).contains(
                "kotlin.ranges.IntProgression",
                "kotlin.ranges.ClosedRange<kotlin.Int>",
                "kotlin.collections.Iterable<kotlin.Int>",
                "kotlin.Any");
        assertThat(KClasses.isSubclassOf(intRange, JvmClassMappingKt.getKotlinClass(Iterable.class))).isTrue();
        assertThat(KTypes.isSupertypeOf(type(Iterable.class, of(INT)), type(IntRange.class))).isTrue();
    }

    @Test
    void rejectsMissingAmbiguousAndNullableBindings() {
        TypedServiceRegistry registry = populatedRegistry(
                new IntRange(0, 9), new CharRange('0', '9'), new ArrayDeque<>());

        assertThatThrownBy(() -> registry.resolve(type(List.class, of(INT))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no binding for kotlin.collections.List<kotlin.Int>");
        assertThatThrownBy(() -> registry.resolve(type(Iterable.class, KTypeProjection.covariant(type(Comparable.class,
                KTypeProjection.star)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ambiguous binding for kotlin.collections.Iterable<out kotlin.Comparable<*>>: "
                        + "[kotlin.ranges.IntRange, kotlin.ranges.CharRange, kotlin.collections.ArrayDeque<kotlin.String>]");

        KType optionalRegion = KTypes.withNullability(type(Long.class), true);
        registry.bind(optionalRegion, 42L);
        assertThat(registry.candidates(type(Long.class))).isEmpty();
        assertThat(registry.resolve(optionalRegion)).isEqualTo(42L);
    }
}
