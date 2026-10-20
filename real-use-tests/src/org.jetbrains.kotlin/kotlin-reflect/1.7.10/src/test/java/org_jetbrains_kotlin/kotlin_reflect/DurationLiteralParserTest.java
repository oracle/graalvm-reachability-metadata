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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KProperty2;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.time.Duration;
import kotlinx.coroutines.selects.SelectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Extension-member reflection: a configuration loader parses duration literals such as
 * `30.seconds` by resolving the member extension property of `Duration.Companion` whose name is
 * the unit and whose extension receiver is the number's Kotlin type.
 *
 * The loader knows no unit names. `KClasses.getMemberExtensionProperties` supplies the units a
 * receiver type supports, `KCallables.getExtensionReceiverParameter` tells which number type each
 * one extends, and the property is made accessible through `KCallablesJvm` and read with both
 * receivers bound, since its `@InlineOnly` getter is private on the JVM. The resulting value-class
 * `Duration` is turned into milliseconds through its own Kotlin property model. The same
 * extension lookup documents the member extension functions a coroutine DSL scope offers.
 */
class DurationLiteralParserTest {

    /** Parses `<number>.<unit>` literals into Kotlin durations through extension reflection. */
    static final class DurationLiteralParser {

        private static final KClass<?> COMPANION =
                KClasses.getCompanionObject(JvmClassMappingKt.getKotlinClass(Duration.class));

        /** The units available for receivers of the Kotlin type `receiverType`, sorted. */
        static List<String> units(String receiverType) {
            List<String> names = new ArrayList<>();
            for (KProperty2<?, ?, ?> property : KClasses.getMemberExtensionProperties(COMPANION)) {
                if (receiverTypeName(property).equals(receiverType)) {
                    names.add(property.getName());
                }
            }
            names.sort(null);
            return names;
        }

        /** The `Duration` the literal denotes, boxed as Kotlin reflection returns value classes. */
        static Object parse(String literal) {
            int dot = literal.lastIndexOf('.');
            if (dot <= 0 || dot == literal.length() - 1) {
                throw new IllegalArgumentException("not a duration literal: " + literal);
            }
            String number = literal.substring(0, dot);
            String unit = literal.substring(dot + 1);
            Object receiver;
            String receiverType;
            if (number.endsWith("L")) {
                receiver = Long.parseLong(number.substring(0, number.length() - 1));
                receiverType = "kotlin.Long";
            } else if (number.contains(".")) {
                receiver = Double.parseDouble(number);
                receiverType = "kotlin.Double";
            } else {
                receiver = Integer.parseInt(number);
                receiverType = "kotlin.Int";
            }
            KProperty2<?, ?, ?> property = unitProperty(unit, receiverType);
            // The unit properties are `@InlineOnly`, so their JVM getters are private.
            if (!KCallablesJvm.isAccessible(property)) {
                KCallablesJvm.setAccessible(property, true);
            }
            return property.call(COMPANION.getObjectInstance(), receiver);
        }

        /** The whole milliseconds of the literal, read through `Duration.inWholeMilliseconds`. */
        static long millis(String literal) {
            Object duration = parse(literal);
            KClass<?> durationClass = JvmClassMappingKt.getKotlinClass(Duration.class);
            for (KProperty1<?, ?> property : KClasses.getMemberProperties(durationClass)) {
                if (property.getName().equals("inWholeMilliseconds")) {
                    return (Long) property.call(duration);
                }
            }
            throw new IllegalStateException("Duration has no inWholeMilliseconds property");
        }

        /** Each member extension function of `type` as `name: receiver`, sorted. */
        static List<String> extensionFunctions(Class<?> type) {
            List<String> signatures = new ArrayList<>();
            for (KFunction<?> function : KClasses.getMemberExtensionFunctions(JvmClassMappingKt.getKotlinClass(type))) {
                signatures.add(function.getName() + ": " + receiverTypeName(function));
            }
            signatures.sort(null);
            return signatures;
        }

        private static KProperty2<?, ?, ?> unitProperty(String unit, String receiverType) {
            for (KProperty2<?, ?, ?> property : KClasses.getMemberExtensionProperties(COMPANION)) {
                if (property.getName().equals(unit) && receiverTypeName(property).equals(receiverType)) {
                    return property;
                }
            }
            throw new IllegalArgumentException("unknown unit " + unit + " for " + receiverType);
        }

        private static String receiverTypeName(KCallable<?> callable) {
            KParameter receiver = KCallables.getExtensionReceiverParameter(callable);
            if (receiver == null) {
                throw new IllegalStateException(callable.getName() + " is not an extension");
            }
            KClassifier classifier = receiver.getType().getClassifier();
            return ((KClass<?>) classifier).getQualifiedName();
        }
    }

    @Test
    void discoversUnitsFromCompanionExtensionProperties() {
        List<String> units = List.of("days", "hours", "microseconds", "milliseconds", "minutes", "nanoseconds", "seconds");
        assertThat(DurationLiteralParser.units("kotlin.Int")).isEqualTo(units);
        assertThat(DurationLiteralParser.units("kotlin.Long")).isEqualTo(units);
        assertThat(DurationLiteralParser.units("kotlin.Double")).isEqualTo(units);
        assertThat(DurationLiteralParser.units("kotlin.String")).isEmpty();
    }

    @Test
    void describesExtensionReceiverParameters() {
        KClass<?> companion = KClasses.getCompanionObject(JvmClassMappingKt.getKotlinClass(Duration.class));
        KProperty2<?, ?, ?> seconds = KClasses.getMemberExtensionProperties(companion).stream()
                .filter(property -> property.getName().equals("seconds"))
                .filter(property -> KCallables.getExtensionReceiverParameter(property).getType().toString()
                        .equals("kotlin.Int"))
                .findFirst()
                .orElseThrow();

        List<KParameter> parameters = seconds.getParameters();
        assertThat(parameters).hasSize(2);
        assertThat(parameters.get(0).getKind()).isEqualTo(KParameter.Kind.INSTANCE);
        assertThat(parameters.get(0)).isEqualTo(KCallables.getInstanceParameter(seconds));
        assertThat(parameters.get(1).getKind()).isEqualTo(KParameter.Kind.EXTENSION_RECEIVER);
        assertThat(KCallables.getValueParameters(seconds)).isEmpty();
        assertThat(seconds.getReturnType().getClassifier()).isEqualTo(JvmClassMappingKt.getKotlinClass(Duration.class));

        Map<String, Boolean> plainProperties = new TreeMap<>();
        for (KProperty1<?, ?> property : KClasses.getMemberProperties(companion)) {
            plainProperties.put(property.getName(), KCallables.getExtensionReceiverParameter(property) == null);
        }
        assertThat(plainProperties).doesNotContainKey("seconds").containsEntry("ZERO", true);
    }

    @Test
    void parsesLiteralsIntoDurations() {
        assertThat(DurationLiteralParser.parse("30.seconds")).isInstanceOf(Duration.class).hasToString("30s");
        assertThat(DurationLiteralParser.parse("250.milliseconds")).hasToString("250ms");
        assertThat(DurationLiteralParser.parse("1.5.hours")).hasToString("1h 30m");
        assertThat(DurationLiteralParser.parse("3L.days")).hasToString("3d");
        assertThat(DurationLiteralParser.parse("30.seconds")).isEqualTo(DurationLiteralParser.parse("30L.seconds"));

        assertThat(DurationLiteralParser.millis("30.seconds")).isEqualTo(30_000L);
        assertThat(DurationLiteralParser.millis("1.5.hours")).isEqualTo(5_400_000L);
        assertThat(DurationLiteralParser.millis("2L.minutes")).isEqualTo(120_000L);
        assertThat(DurationLiteralParser.millis("1500000.microseconds")).isEqualTo(1_500L);
    }

    @Test
    void rejectsUnknownUnitsAndMalformedLiterals() {
        assertThatThrownBy(() -> DurationLiteralParser.parse("4.weeks"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown unit weeks for kotlin.Int");
        assertThatThrownBy(() -> DurationLiteralParser.parse("seconds"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a duration literal: seconds");
        assertThatThrownBy(() -> DurationLiteralParser.parse("30."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a duration literal: 30.");
    }

    @Test
    void listsMemberExtensionFunctionsOfADslScope() {
        assertThat(DurationLiteralParser.extensionFunctions(SelectBuilder.class)).containsExactly(
                "invoke: kotlinx.coroutines.selects.SelectClause0",
                "invoke: kotlinx.coroutines.selects.SelectClause1",
                "invoke: kotlinx.coroutines.selects.SelectClause2",
                "invoke: kotlinx.coroutines.selects.SelectClause2");
        assertThat(KClasses.getMemberFunctions(JvmClassMappingKt.getKotlinClass(SelectBuilder.class)))
                .extracting(KCallable::getName)
                .contains("onTimeout")
                .doesNotContain("invoke");
    }
}
