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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.collections.ArrayDeque;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.CharRange;
import kotlin.ranges.ClosedFloatingPointRange;
import kotlin.ranges.ClosedRange;
import kotlin.ranges.IntProgression;
import kotlin.ranges.IntRange;
import kotlin.reflect.KClass;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeParameter;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.KVariance;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.full.KClassifiers;
import kotlin.reflect.full.KTypes;
import org.junit.jupiter.api.Test;

/**
 * Generic supertype resolution: a converter registry finds the element type a Kotlin collection
 * or range supplies to `Iterable` by walking the declared `KClass.supertypes` and substituting
 * each class's `KTypeParameter`s with the arguments of the requested `KType`.
 *
 * Declared supertypes still reference the subclass's own type parameters, so `ArrayDeque<String>`
 * only becomes `Iterable<String>` after `E` is replaced along the chain; nullability and star
 * projections survive the substitution, and declaration-site variance and upper bounds come from
 * the type parameters themselves.
 */
class GenericSupertypeResolverTest {

    /** Resolves the arguments a concrete `KType` passes to one of its generic superclasses. */
    static final class SupertypeResolver {

        /** The arguments `type` supplies to `target`, after substituting every type parameter. */
        List<KTypeProjection> argumentsFor(KType type, KClass<?> target) {
            KClass<?> kClass = (KClass<?>) type.getClassifier();
            if (kClass.equals(target)) {
                return type.getArguments();
            }
            if (!KClasses.isSubclassOf(kClass, target)) {
                throw new IllegalArgumentException(type + " does not extend " + target.getQualifiedName());
            }
            Map<String, KTypeProjection> bindings = bind(kClass, type.getArguments());
            for (KType supertype : kClass.getSupertypes()) {
                KClassifier classifier = supertype.getClassifier();
                if (classifier instanceof KClass<?> superclass && KClasses.isSubclassOf(superclass, target)) {
                    return argumentsFor(substitute(supertype, bindings), target);
                }
            }
            throw new IllegalStateException("No supertype of " + type + " leads to " + target.getQualifiedName());
        }

        /** The single element type `type` iterates over, or `null` for a star projection. */
        KType elementType(KType type) {
            List<KTypeProjection> arguments = argumentsFor(type, JvmClassMappingKt.getKotlinClass(Iterable.class));
            assertThat(arguments).hasSize(1);
            return arguments.get(0).getType();
        }

        private static Map<String, KTypeProjection> bind(KClass<?> kClass, List<KTypeProjection> arguments) {
            List<KTypeParameter> parameters = kClass.getTypeParameters();
            if (parameters.size() != arguments.size()) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " expects " + parameters.size()
                        + " type arguments but got " + arguments.size());
            }
            Map<String, KTypeProjection> bindings = new HashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                bindings.put(parameters.get(i).getName(), arguments.get(i));
            }
            return bindings;
        }

        private static KType substitute(KType type, Map<String, KTypeProjection> bindings) {
            List<KTypeProjection> arguments = new ArrayList<>();
            for (KTypeProjection argument : type.getArguments()) {
                arguments.add(substitute(argument, bindings));
            }
            return KClassifiers.createType(type.getClassifier(), arguments, type.isMarkedNullable(),
                    Collections.emptyList());
        }

        private static KTypeProjection substitute(KTypeProjection projection, Map<String, KTypeProjection> bindings) {
            KType type = projection.getType();
            if (type == null) {
                return projection;
            }
            if (type.getClassifier() instanceof KTypeParameter parameter) {
                KTypeProjection bound = bindings.get(parameter.getName());
                if (bound == null) {
                    throw new IllegalStateException("Unbound type parameter " + parameter.getName());
                }
                if (bound.getType() == null) {
                    return bound;
                }
                KType resolved = type.isMarkedNullable()
                        ? KTypes.withNullability(bound.getType(), true)
                        : bound.getType();
                KVariance variance = projection.getVariance() == KVariance.INVARIANT
                        ? bound.getVariance()
                        : projection.getVariance();
                return new KTypeProjection(variance, resolved);
            }
            return new KTypeProjection(projection.getVariance(), substitute(type, bindings));
        }
    }

    private static final KType STRING = KClassifiers.getStarProjectedType(kotlin(String.class));
    private static final KType INT = KClassifiers.getStarProjectedType(kotlin(Integer.class));
    private static final KType CHAR = KClassifiers.getStarProjectedType(kotlin(Character.class));

    private static KClass<?> kotlin(Class<?> type) {
        return JvmClassMappingKt.getKotlinClass(type);
    }

    private static KType type(Class<?> type, KTypeProjection... arguments) {
        return KClassifiers.createType(kotlin(type), List.of(arguments), false, Collections.emptyList());
    }

    private final SupertypeResolver resolver = new SupertypeResolver();

    @Test
    void declaredSupertypesStillReferenceTheSubclassTypeParameter() {
        KClass<?> deque = kotlin(ArrayDeque.class);
        KTypeParameter element = deque.getTypeParameters().get(0);
        assertThat(element.getName()).isEqualTo("E");

        KType supertype = deque.getSupertypes().get(0);
        assertThat(((KClass<?>) supertype.getClassifier()).getQualifiedName())
                .isEqualTo("kotlin.collections.AbstractMutableList");
        KType argument = supertype.getArguments().get(0).getType();
        assertThat(argument.getClassifier()).isInstanceOf(KTypeParameter.class);
        assertThat(((KTypeParameter) argument.getClassifier()).getName()).isEqualTo("E");
    }

    @Test
    void resolvesTheElementTypeOfAGenericCollectionThroughItsSuperclassChain() {
        KType dequeOfStrings = type(ArrayDeque.class, KTypeProjection.invariant(STRING));

        assertThat(resolver.elementType(dequeOfStrings)).isEqualTo(STRING);
        assertThat(resolver.argumentsFor(dequeOfStrings, kotlin(Collection.class)))
                .containsExactly(KTypeProjection.invariant(STRING));
    }

    @Test
    void resolvesTheFixedElementTypeOfNonGenericRanges() {
        KType intRange = type(IntRange.class);
        KType charRange = type(CharRange.class);

        assertThat(resolver.elementType(intRange)).isEqualTo(INT);
        assertThat(resolver.elementType(charRange)).isEqualTo(CHAR);
        assertThat(resolver.argumentsFor(intRange, kotlin(ClosedRange.class)))
                .containsExactly(KTypeProjection.invariant(INT));
        assertThat(resolver.argumentsFor(type(IntProgression.class), kotlin(Iterable.class)))
                .containsExactly(KTypeProjection.invariant(INT));
    }

    @Test
    void keepsNullableArgumentsAndStarProjections() {
        KType nullableString = KTypes.withNullability(STRING, true);
        KType dequeOfNullables = type(ArrayDeque.class, KTypeProjection.invariant(nullableString));
        KType anyDeque = type(ArrayDeque.class, KTypeProjection.star);

        KType element = resolver.elementType(dequeOfNullables);
        assertThat(element.isMarkedNullable()).isTrue();
        assertThat(element.getClassifier()).isEqualTo(kotlin(String.class));
        assertThat(resolver.argumentsFor(anyDeque, kotlin(Iterable.class))).containsExactly(KTypeProjection.star);
        assertThat(resolver.elementType(anyDeque)).isNull();
    }

    @Test
    void walksTheWholeSuperclassHierarchy() {
        KClass<?> intRange = kotlin(IntRange.class);
        List<String> names = new ArrayList<>();
        for (KClass<?> superclass : KClasses.getAllSuperclasses(intRange)) {
            names.add(superclass.getQualifiedName());
        }

        assertThat(names).contains("kotlin.ranges.IntProgression", "kotlin.ranges.ClosedRange",
                "kotlin.collections.Iterable", "kotlin.Any");
        assertThat(KClasses.isSuperclassOf(kotlin(Iterable.class), intRange)).isTrue();
        assertThat(KClasses.isSuperclassOf(kotlin(Collection.class), intRange)).isFalse();
        assertThatThrownBy(() -> resolver.argumentsFor(type(IntRange.class), kotlin(Collection.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not extend kotlin.collections.Collection");
    }

    @Test
    void readsDeclarationSiteVarianceAndUpperBounds() {
        KTypeParameter rangeBound = kotlin(ClosedFloatingPointRange.class).getTypeParameters().get(0);
        KType bound = rangeBound.getUpperBounds().get(0);
        assertThat(bound.getClassifier()).isEqualTo(kotlin(Comparable.class));
        assertThat(bound.getArguments().get(0).getType().getClassifier()).isEqualTo(rangeBound);

        KTypeParameter element = kotlin(ArrayDeque.class).getTypeParameters().get(0);
        assertThat(element.getVariance()).isEqualTo(KVariance.INVARIANT);
        assertThat(element.getUpperBounds()).hasSize(1);
        assertThat(element.getUpperBounds().get(0).isMarkedNullable()).isTrue();
        assertThat(element.getUpperBounds().get(0).getClassifier()).isEqualTo(kotlin(Object.class));

        assertThat(kotlin(Iterable.class).getTypeParameters().get(0).getVariance()).isEqualTo(KVariance.OUT);
        assertThat(kotlin(Comparable.class).getTypeParameters().get(0).getVariance()).isEqualTo(KVariance.IN);
    }
}
