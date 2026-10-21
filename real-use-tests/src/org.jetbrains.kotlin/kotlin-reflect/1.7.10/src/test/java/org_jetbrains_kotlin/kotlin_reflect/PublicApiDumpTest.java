/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.DeepRecursiveScope;
import kotlin.KotlinVersion;
import kotlin.Pair;
import kotlin.collections.AbstractList;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.IntProgression;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KMutableProperty;
import kotlin.reflect.KProperty;
import kotlin.reflect.KTypeParameter;
import kotlin.reflect.KVariance;
import kotlin.reflect.KVisibility;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlin.text.Regex;
import kotlin.time.Duration;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.flow.FlowCollector;
import org.junit.jupiter.api.Test;

/**
 * Declaration modifier and visibility reflection: an API-dump tool renders the public surface of
 * Kotlin classes from `KClass` and `KCallable` modifiers, the way a binary-compatibility check
 * records a library's API before a release.
 *
 * The dumper reads every modifier from the Kotlin model rather than from JVM flags: class
 * modality, `sealed`, `data`, `value` and `fun interface` from `KClass`, `operator`, `infix`,
 * `suspend` and `inline` from `KFunction`, `const`, `lateinit` and `var` from `KProperty`, and
 * `KVisibility` to drop `internal` and `private` declarations that the JVM sees as public.
 * A stub generator then asks the same model which members a subclass must implement.
 */
class PublicApiDumpTest {

    /** API dump tool that renders the public surface of a Kotlin class. */
    static final class ApiDumper {

        /** The declaration line of `type`: visibility, modality, kind, name and type parameters. */
        static String header(Class<?> type) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            List<String> words = new ArrayList<>();
            words.add(visibility(kClass.getVisibility()));
            if (type.isInterface()) {
                words.add(kClass.isFun() ? "fun interface" : "interface");
            } else {
                words.add(modality(kClass));
                if (kClass.isData()) {
                    words.add("data");
                }
                if (kClass.isValue()) {
                    words.add("value");
                }
                if (type.isEnum()) {
                    words.add("enum");
                }
                words.add("class");
            }
            words.add(kClass.getQualifiedName() + typeParameters(kClass.getTypeParameters()));
            return String.join(" ", words);
        }

        /**
         * Sorted lines for every public declared member of `type`, followed by the public
         * members of its companion object; `internal` and `private` members are left out.
         */
        static List<String> dump(Class<?> type) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            List<String> lines = members(kClass, "");
            KClass<?> companion = KClasses.getCompanionObject(kClass);
            if (companion != null) {
                lines.addAll(members(companion, "companion "));
            }
            return lines;
        }

        /** Signatures of the members a concrete subclass of `type` must implement. */
        static List<String> abstractMembers(Class<?> type) {
            return JvmClassMappingKt.getKotlinClass(type).getMembers().stream()
                    .filter(KCallable::isAbstract)
                    .map(ApiDumper::signature)
                    .sorted()
                    .collect(Collectors.toList());
        }

        /** Signatures of the declared members of `type` a subclass may override. */
        static List<String> overridableMembers(Class<?> type) {
            return KClasses.getDeclaredMembers(JvmClassMappingKt.getKotlinClass(type)).stream()
                    .filter(member -> member.isOpen() && isPublicApi(member.getVisibility()))
                    .map(ApiDumper::signature)
                    .sorted()
                    .distinct()
                    .collect(Collectors.toList());
        }

        /** Header lines of the subclasses a sealed class lets callers see. */
        static List<String> publicSealedSubclasses(Class<?> type) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            if (!kClass.isSealed()) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " is not sealed");
            }
            return kClass.getSealedSubclasses().stream()
                    .filter(subclass -> isPublicApi(subclass.getVisibility()))
                    .map(subclass -> header(JvmClassMappingKt.getJavaClass(subclass)))
                    .collect(Collectors.toList());
        }

        /** The public infix function `name` of `receiver`'s class, applied to `argument`. */
        static Object applyInfix(Object receiver, String name, Object argument) {
            for (KCallable<?> member : KClasses.getDeclaredMembers(JvmClassMappingKt.getKotlinClass(receiver.getClass()))) {
                if (member instanceof KFunction<?> function && function.isInfix() && function.getName().equals(name)
                        && isPublicApi(function.getVisibility())) {
                    return function.call(receiver, argument);
                }
            }
            throw new IllegalArgumentException("no public infix function " + name);
        }

        private static List<String> members(KClass<?> kClass, String prefix) {
            return KClasses.getDeclaredMembers(kClass).stream()
                    .filter(member -> isPublicApi(member.getVisibility()))
                    .map(member -> prefix + line(member))
                    .sorted()
                    .collect(Collectors.toList());
        }

        private static String line(KCallable<?> member) {
            List<String> words = new ArrayList<>();
            words.add(visibility(member.getVisibility()));
            if (member instanceof KProperty<?> property) {
                if (property.isConst()) {
                    words.add("const");
                } else {
                    words.add(modality(member));
                }
                if (property.isLateinit()) {
                    words.add("lateinit");
                }
                words.add(member instanceof KMutableProperty<?> ? "var" : "val");
            } else {
                KFunction<?> function = (KFunction<?>) member;
                words.add(modality(member));
                if (function.isInline()) {
                    words.add("inline");
                }
                if (function.isInfix()) {
                    words.add("infix");
                }
                if (function.isOperator()) {
                    words.add("operator");
                }
                if (function.isSuspend()) {
                    words.add("suspend");
                }
                words.add("fun");
            }
            words.add(signature(member));
            return String.join(" ", words);
        }

        private static String signature(KCallable<?> member) {
            String returnType = member.getReturnType().toString();
            if (member instanceof KProperty<?>) {
                return member.getName() + ": " + returnType;
            }
            String parameters = KCallables.getValueParameters(member).stream()
                    .map(parameter -> parameter.getType().toString())
                    .collect(Collectors.joining(", "));
            return member.getName() + "(" + parameters + "): " + returnType;
        }

        private static String modality(KClass<?> kClass) {
            if (kClass.isSealed()) {
                return "sealed";
            }
            if (kClass.isAbstract()) {
                return "abstract";
            }
            return kClass.isOpen() ? "open" : "final";
        }

        private static String modality(KCallable<?> member) {
            if (member.isAbstract()) {
                return "abstract";
            }
            return member.isOpen() ? "open" : "final";
        }

        private static String typeParameters(List<KTypeParameter> parameters) {
            if (parameters.isEmpty()) {
                return "";
            }
            return parameters.stream()
                    .map(parameter -> variance(parameter.getVariance()) + parameter.getName())
                    .collect(Collectors.joining(", ", "<", ">"));
        }

        private static String variance(KVariance variance) {
            return switch (variance) {
                case IN -> "in ";
                case OUT -> "out ";
                case INVARIANT -> "";
            };
        }

        private static boolean isPublicApi(KVisibility visibility) {
            return visibility == KVisibility.PUBLIC || visibility == KVisibility.PROTECTED;
        }

        private static String visibility(KVisibility visibility) {
            return visibility == null ? "local" : visibility.name().toLowerCase();
        }
    }

    @Test
    void rendersClassHeadersForEveryDeclarationKind() {
        assertThat(ApiDumper.header(Duration.class)).isEqualTo("public final value class kotlin.time.Duration");
        assertThat(ApiDumper.header(Pair.class)).isEqualTo("public final data class kotlin.Pair<out A, out B>");
        assertThat(ApiDumper.header(IntProgression.class)).isEqualTo("public open class kotlin.ranges.IntProgression");
        assertThat(ApiDumper.header(AbstractList.class))
                .isEqualTo("public abstract class kotlin.collections.AbstractList<out E>");
        assertThat(ApiDumper.header(DeepRecursiveScope.class))
                .isEqualTo("public sealed class kotlin.DeepRecursiveScope<T, R>");
        assertThat(ApiDumper.header(FlowCollector.class))
                .isEqualTo("public fun interface kotlinx.coroutines.flow.FlowCollector<in T>");
        assertThat(ApiDumper.header(Job.class)).isEqualTo("public interface kotlinx.coroutines.Job");
        assertThat(ApiDumper.header(CoroutineStart.class))
                .isEqualTo("public final enum class kotlinx.coroutines.CoroutineStart");
    }

    @Test
    void dumpsThePublicSurfaceAndHidesInternals() {
        List<String> regex = ApiDumper.dump(Regex.class);
        assertThat(regex).contains(
                "public final infix fun matches(kotlin.CharSequence): kotlin.Boolean",
                "public final val pattern: kotlin.String",
                "public final val options: kotlin.collections.Set<kotlin.text.RegexOption>",
                "public open fun toString(): kotlin.String");
        assertThat(regex).noneMatch(line -> line.contains("_options") || line.contains("nativePattern")
                || line.contains("writeReplace"));

        assertThat(ApiDumper.dump(KotlinVersion.class)).containsExactly(
                "public final fun isAtLeast(kotlin.Int, kotlin.Int): kotlin.Boolean",
                "public final fun isAtLeast(kotlin.Int, kotlin.Int, kotlin.Int): kotlin.Boolean",
                "public final val major: kotlin.Int",
                "public final val minor: kotlin.Int",
                "public final val patch: kotlin.Int",
                "public open fun hashCode(): kotlin.Int",
                "public open fun toString(): kotlin.String",
                "public open operator fun compareTo(kotlin.KotlinVersion): kotlin.Int",
                "public open operator fun equals(kotlin.Any?): kotlin.Boolean",
                "companion public const val MAX_COMPONENT_VALUE: kotlin.Int",
                "companion public final val CURRENT: kotlin.KotlinVersion");

        List<String> duration = ApiDumper.dump(Duration.class);
        assertThat(duration).contains(
                "public final operator fun plus(kotlin.time.Duration): kotlin.time.Duration",
                "public final operator fun unaryMinus(): kotlin.time.Duration",
                "public final val absoluteValue: kotlin.time.Duration");
        assertThat(duration).noneMatch(line -> line.contains("hoursComponent") || line.contains("rawValue"));

        assertThat(ApiDumper.dump(KotlinModule.class)).contains(
                "public open fun setupModule(com.fasterxml.jackson.databind.Module.SetupContext): kotlin.Unit",
                "public final val strictNullChecks: kotlin.Boolean",
                "companion public const val serialVersionUID: kotlin.Long")
                .noneMatch(line -> line.contains("ignoredClassesForImplyingJsonCreator"));
    }

    @Test
    void listsTheMembersAnImplementationMustProvide() {
        assertThat(ApiDumper.abstractMembers(AbstractList.class)).containsExactly(
                "get(kotlin.Int): E",
                "size: kotlin.Int");

        assertThat(ApiDumper.abstractMembers(FlowCollector.class)).containsExactly("emit(T): kotlin.Unit");
        assertThat(ApiDumper.dump(FlowCollector.class))
                .containsExactly("public abstract suspend fun emit(T): kotlin.Unit");

        assertThat(ApiDumper.abstractMembers(IntProgression.class)).isEmpty();
        assertThat(ApiDumper.overridableMembers(IntProgression.class)).containsExactly(
                "equals(kotlin.Any?): kotlin.Boolean",
                "hashCode(): kotlin.Int",
                "isEmpty(): kotlin.Boolean",
                "iterator(): kotlin.collections.IntIterator",
                "toString(): kotlin.String");
        assertThat(ApiDumper.overridableMembers(Regex.class)).containsExactly("toString(): kotlin.String");
    }

    @Test
    void sealedClassesExposeNoPublicSubclasses() {
        KClass<?> scope = JvmClassMappingKt.getKotlinClass(DeepRecursiveScope.class);

        assertThat(scope.getSealedSubclasses()).singleElement().satisfies(subclass -> {
            assertThat(subclass.getVisibility()).isNotIn(KVisibility.PUBLIC, KVisibility.PROTECTED);
            assertThat(KClasses.isSubclassOf(subclass, scope)).isTrue();
        });
        assertThat(ApiDumper.publicSealedSubclasses(DeepRecursiveScope.class)).isEmpty();
        assertThat(ApiDumper.dump(DeepRecursiveScope.class)).containsExactly(
                "public abstract suspend fun callRecursive(T): R",
                "public abstract suspend fun callRecursive(U): S",
                "public final operator fun invoke(kotlin.Any?): kotlin.Nothing");
        assertThat(JvmClassMappingKt.getKotlinClass(Regex.class).getSealedSubclasses()).isEmpty();
    }

    @Test
    void appliesAnInfixFunctionFoundThroughItsModifier() {
        Regex identifier = new Regex("[a-z][a-z0-9]*");

        assertThat(ApiDumper.applyInfix(identifier, "matches", "order42")).isEqualTo(true);
        assertThat(ApiDumper.applyInfix(identifier, "matches", "42order")).isEqualTo(false);
        assertThat(identifier.matches("order42")).isTrue();
    }
}
