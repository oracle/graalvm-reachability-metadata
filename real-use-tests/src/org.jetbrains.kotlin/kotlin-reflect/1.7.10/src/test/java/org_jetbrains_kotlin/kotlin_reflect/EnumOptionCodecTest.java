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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import kotlin.LazyThreadSafetyMode;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.IntRange;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KType;
import kotlin.reflect.KVisibility;
import kotlin.reflect.full.KClasses;
import kotlin.text.Regex;
import kotlin.text.RegexOption;
import kotlin.time.DurationUnit;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.channels.BufferOverflow;
import org.junit.jupiter.api.Test;

/**
 * Kotlin enum-class reflection: a command-line option codec maps kebab-case option values onto
 * the constants of Kotlin enum classes through the `values` and `valueOf` functions in
 * `KClasses.getStaticFunctions`, recognises an enum by its `kotlin.Enum<Self>` supertype, and
 * reports each constant's public attributes through `KClasses.getDeclaredMemberProperties`.
 *
 * The enums come from the Kotlin standard library and kotlinx.coroutines: `RegexOption` carries
 * public `value` and `mask` properties, `CoroutineStart` a public `isLazy` property, and
 * `DurationUnit` an internal `timeUnit` property that the codec must not publish.
 */
class EnumOptionCodecTest {

    /** Decodes and encodes enum option values the way a command-line parser would. */
    static final class EnumOptionCodec {

        private static final KClass<?> ENUM = JvmClassMappingKt.getKotlinClass(Enum.class);
        private static final KClass<?> STRING = JvmClassMappingKt.getKotlinClass(String.class);

        /** Whether `kClass` is a Kotlin enum class, that is, whether it extends `kotlin.Enum<Self>`. */
        boolean isEnumClass(KClass<?> kClass) {
            for (KType supertype : kClass.getSupertypes()) {
                if (ENUM.equals(supertype.getClassifier())) {
                    KType self = supertype.getArguments().get(0).getType();
                    return self != null && kClass.equals(self.getClassifier());
                }
            }
            return false;
        }

        /** Every constant of `enumClass`, in declaration order, from its static `values()` function. */
        List<Object> constants(KClass<?> enumClass) {
            Object[] values = (Object[]) staticFunction(enumClass, "values", 0).call();
            return List.of(values);
        }

        /** The option values `enumClass` accepts, in declaration order. */
        List<String> choices(KClass<?> enumClass) {
            List<String> choices = new ArrayList<>();
            for (Object constant : constants(enumClass)) {
                choices.add(encode(constant));
            }
            return choices;
        }

        /** The constant of `enumClass` named by the kebab-case option value `text`. */
        <T> T decode(KClass<T> enumClass, String text) {
            List<String> choices = choices(enumClass);
            String option = text.trim().toLowerCase(Locale.ROOT);
            if (!choices.contains(option)) {
                throw new IllegalArgumentException("Unknown " + optionName(enumClass) + " '" + text
                        + "'; expected one of: " + String.join(", ", choices));
            }
            KFunction<?> valueOf = staticFunction(enumClass, "valueOf", 1);
            assertThat(valueOf.getParameters().get(0).getType().getClassifier()).isEqualTo(STRING);
            Object constant = valueOf.call(option.replace('-', '_').toUpperCase(Locale.ROOT));
            return KClasses.cast(enumClass, constant);
        }

        /** Every constant named in the comma-separated option list `text`. */
        <T> Set<T> decodeAll(KClass<T> enumClass, String text) {
            Set<T> constants = new LinkedHashSet<>();
            for (String option : text.split(",")) {
                if (!option.isBlank()) {
                    constants.add(decode(enumClass, option));
                }
            }
            return constants;
        }

        /** The kebab-case option value of `constant`, from its `name` property. */
        String encode(Object constant) {
            Object name = memberProperty(kotlinClassOf(constant), "name").call(constant);
            return ((String) name).toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** The position of `constant` in its enum, from its `ordinal` property. */
        int ordinal(Object constant) {
            return (Integer) memberProperty(kotlinClassOf(constant), "ordinal").call(constant);
        }

        /** The public properties `constant`'s enum class declares itself, by name. */
        Map<String, Object> attributes(Object constant) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (KProperty1<?, ?> property : KClasses.getDeclaredMemberProperties(kotlinClassOf(constant))) {
                if (property.getVisibility() == KVisibility.PUBLIC) {
                    attributes.put(property.getName(), property.call(constant));
                }
            }
            return attributes;
        }

        private KClass<?> kotlinClassOf(Object constant) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(((Enum<?>) constant).getDeclaringClass());
            if (!isEnumClass(kClass)) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " is not an enum class");
            }
            return kClass;
        }

        private KFunction<?> staticFunction(KClass<?> enumClass, String name, int arity) {
            if (!isEnumClass(enumClass)) {
                throw new IllegalArgumentException(enumClass.getQualifiedName() + " is not an enum class");
            }
            for (KFunction<?> function : KClasses.getStaticFunctions(enumClass)) {
                if (function.getName().equals(name) && function.getParameters().size() == arity) {
                    return function;
                }
            }
            throw new IllegalStateException(enumClass.getQualifiedName() + " has no static " + name);
        }

        private static KProperty1<?, ?> memberProperty(KClass<?> kClass, String name) {
            for (KProperty1<?, ?> property : KClasses.getMemberProperties(kClass)) {
                if (property.getName().equals(name)) {
                    return property;
                }
            }
            throw new IllegalStateException(kClass.getQualifiedName() + " has no property " + name);
        }

        private static String optionName(KClass<?> enumClass) {
            return enumClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        }
    }

    private static <T> KClass<T> kotlin(Class<T> type) {
        return JvmClassMappingKt.getKotlinClass(type);
    }

    private final EnumOptionCodec codec = new EnumOptionCodec();

    @Test
    void recognisesEnumClassesByTheirSelfReferencingEnumSupertype() {
        assertThat(codec.isEnumClass(kotlin(RegexOption.class))).isTrue();
        assertThat(codec.isEnumClass(kotlin(CoroutineStart.class))).isTrue();
        assertThat(codec.isEnumClass(kotlin(IntRange.class))).isFalse();
        assertThat(codec.isEnumClass(kotlin(String.class))).isFalse();

        KClass<RegexOption> regexOption = kotlin(RegexOption.class);
        assertThat(regexOption.isFinal()).isTrue();
        assertThat(KClasses.getPrimaryConstructor(regexOption).getVisibility()).isEqualTo(KVisibility.PRIVATE);
        assertThatThrownBy(() -> codec.choices(kotlin(IntRange.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.ranges.IntRange is not an enum class");
    }

    @Test
    void listsTheChoicesOfAnEnumInDeclarationOrder() {
        assertThat(codec.choices(kotlin(BufferOverflow.class))).containsExactly("suspend", "drop-oldest", "drop-latest");
        assertThat(codec.choices(kotlin(LazyThreadSafetyMode.class))).containsExactly("synchronized", "publication",
                "none");
        assertThat(codec.constants(kotlin(CoroutineStart.class))).containsExactly(CoroutineStart.DEFAULT,
                CoroutineStart.LAZY, CoroutineStart.ATOMIC, CoroutineStart.UNDISPATCHED);
        assertThat(codec.ordinal(CoroutineStart.UNDISPATCHED)).isEqualTo(3);
    }

    @Test
    void decodesKebabCaseOptionValuesThroughValueOf() {
        assertThat(codec.decode(kotlin(BufferOverflow.class), "drop-oldest")).isSameAs(BufferOverflow.DROP_OLDEST);
        assertThat(codec.decode(kotlin(DurationUnit.class), " Milliseconds ")).isSameAs(DurationUnit.MILLISECONDS);
        assertThat(codec.decode(kotlin(CoroutineStart.class), "undispatched")).isSameAs(CoroutineStart.UNDISPATCHED);
        assertThat(codec.encode(LazyThreadSafetyMode.PUBLICATION)).isEqualTo("publication");
        assertThat(codec.encode(RegexOption.DOT_MATCHES_ALL)).isEqualTo("dot-matches-all");
    }

    @Test
    void rejectsUnknownOptionValuesWithTheAcceptedChoices() {
        assertThatThrownBy(() -> codec.decode(kotlin(BufferOverflow.class), "drop-all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown buffer overflow 'drop-all'; expected one of: suspend, drop-oldest, drop-latest");
    }

    @Test
    void decodedRegexOptionsConfigureARegex() {
        Set<RegexOption> options = codec.decodeAll(kotlin(RegexOption.class), "ignore-case,dot-matches-all");
        assertThat(options).containsExactly(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL);

        Regex regex = new Regex("begin.end", options);
        assertThat(regex.matches("BEGIN\nEND")).isTrue();
        assertThat(new Regex("begin.end").matches("BEGIN\nEND")).isFalse();
        assertThat(regex.getOptions()).containsExactlyInAnyOrderElementsOf(options);
    }

    @Test
    void reportsOnlyThePublicAttributesAnEnumDeclares() {
        assertThat(codec.attributes(RegexOption.IGNORE_CASE)).containsOnly(
                Map.entry("value", Pattern.CASE_INSENSITIVE), Map.entry("mask", Pattern.CASE_INSENSITIVE));
        assertThat(codec.attributes(RegexOption.LITERAL)).containsOnly(
                Map.entry("value", Pattern.LITERAL), Map.entry("mask", Pattern.LITERAL));
        assertThat(codec.attributes(CoroutineStart.LAZY)).containsOnly(Map.entry("isLazy", true));
        assertThat(codec.attributes(CoroutineStart.DEFAULT)).containsOnly(Map.entry("isLazy", false));
        assertThat(codec.attributes(DurationUnit.SECONDS)).isEmpty();
        assertThat(codec.attributes(BufferOverflow.SUSPEND)).isEmpty();
    }
}
