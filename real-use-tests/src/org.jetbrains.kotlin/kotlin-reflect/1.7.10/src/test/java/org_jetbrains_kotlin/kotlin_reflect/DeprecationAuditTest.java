/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kotlin.Deprecated;
import kotlin.DeprecationLevel;
import kotlin.annotation.AnnotationTarget;
import kotlin.annotation.MustBeDocumented;
import kotlin.annotation.Target;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KAnnotatedElement;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KType;
import kotlin.reflect.full.KAnnotatedElements;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.channels.BroadcastChannel;
import org.junit.jupiter.api.Test;

/**
 * Annotation reflection on Kotlin declarations: an upgrade auditor finds every `@Deprecated`
 * constructor and member of a library API through `KAnnotatedElement.annotations` and
 * `findAnnotations`, and decodes each annotation through its annotation class's Kotlin model.
 *
 * The auditor never reads Java annotation methods by name. It learns the attribute order from
 * the annotation class's primary constructor, reads each attribute through the matching
 * `KProperty1`, and descends into nested annotations, so the same decoder renders
 * `Deprecated`, its nested `ReplaceWith` and the meta-annotations on the annotation class itself.
 * A migration plan then keeps the findings Kotlin callers can still reach and that name a
 * replacement, and the auditor proves a replacement equivalent by calling the deprecated member
 * through `KFunction.call`.
 */
class DeprecationAuditTest {

    /** Upgrade auditor that reports deprecated API of a Kotlin library. */
    static final class DeprecationAuditor {

        private static final KClass<Deprecated> DEPRECATED = JvmClassMappingKt.getKotlinClass(Deprecated.class);

        /** One deprecated declaration: its signature and the decoded `@Deprecated` attributes. */
        record Finding(String signature, DeprecationLevel level, String message, String replacement,
                List<String> imports) {
        }

        /** Every deprecated constructor and declared member of `api`, ordered by signature. */
        static List<Finding> audit(Class<?> api) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(api);
            List<KCallable<?>> callables = new ArrayList<>(kClass.getConstructors());
            callables.addAll(KClasses.getDeclaredMembers(kClass));
            List<Finding> findings = new ArrayList<>();
            for (KCallable<?> callable : callables) {
                for (Deprecated deprecated : KAnnotatedElements.findAnnotations(callable, DEPRECATED)) {
                    findings.add(finding(signature(callable), deprecated));
                }
            }
            findings.sort(Comparator.comparing(Finding::signature));
            return findings;
        }

        /** Replacement expressions by signature for findings Kotlin code can still call. */
        static Map<String, String> migrationPlan(Class<?> api) {
            Map<String, String> plan = new LinkedHashMap<>();
            for (Finding finding : audit(api)) {
                if (finding.level() != DeprecationLevel.HIDDEN && !finding.replacement().isEmpty()) {
                    plan.put(finding.signature(), finding.replacement());
                }
            }
            return plan;
        }

        /** The declared member of `api` with `signature`; fails when there is none. */
        static KFunction<?> member(Class<?> api, String signature) {
            for (KCallable<?> callable : KClasses.getDeclaredMembers(JvmClassMappingKt.getKotlinClass(api))) {
                if (callable instanceof KFunction<?> function && signature(function).equals(signature)) {
                    return function;
                }
            }
            throw new IllegalArgumentException("no member " + signature + " on " + api.getName());
        }

        /**
         * Attributes of `annotation` in declaration order, read through the Kotlin properties of
         * its annotation class; nested annotations become nested maps, enums their names, and
         * arrays lists.
         */
        static Map<String, Object> attributes(Annotation annotation) {
            KClass<? extends Annotation> type = JvmClassMappingKt.getAnnotationClass(annotation);
            Map<String, KProperty1<?, ?>> properties = new LinkedHashMap<>();
            for (KProperty1<?, ?> property : KClasses.getMemberProperties(type)) {
                properties.put(property.getName(), property);
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (KParameter parameter : KClasses.getPrimaryConstructor(type).getParameters()) {
                @SuppressWarnings("unchecked")
                KProperty1<Object, ?> property = (KProperty1<Object, ?>) properties.get(parameter.getName());
                attributes.put(parameter.getName(), render(property.get(annotation)));
            }
            return attributes;
        }

        /** Simple names of the annotations on `element`, in declaration order. */
        static List<String> annotationNames(KAnnotatedElement element) {
            KAnnotatedElement elementAnnotationAccess = element;
            return elementAnnotationAccess.getAnnotations().stream()
                    .map(annotation -> JvmClassMappingKt.getAnnotationClass(annotation).getSimpleName())
                    .collect(Collectors.toList());
        }

        private static Finding finding(String signature, Deprecated deprecated) {
            Map<String, Object> attributes = attributes(deprecated);
            @SuppressWarnings("unchecked")
            Map<String, Object> replaceWith = (Map<String, Object>) attributes.get("replaceWith");
            @SuppressWarnings("unchecked")
            List<String> imports = (List<String>) replaceWith.get("imports");
            return new Finding(signature, DeprecationLevel.valueOf((String) attributes.get("level")),
                    (String) attributes.get("message"), (String) replaceWith.get("expression"), imports);
        }

        private static Object render(Object value) {
            if (value instanceof Annotation nested) {
                return attributes(nested);
            }
            if (value instanceof Enum<?> constant) {
                return constant.name();
            }
            if (value instanceof Object[] array) {
                return Arrays.stream(array).map(DeprecationAuditor::render).collect(Collectors.toList());
            }
            return value;
        }

        private static String signature(KCallable<?> callable) {
            String parameters = KCallables.getValueParameters(callable).stream()
                    .map(parameter -> typeName(parameter.getType()))
                    .collect(Collectors.joining(", "));
            return callable.getName() + "(" + parameters + ")";
        }

        private static String typeName(KType type) {
            String name = ((KClass<?>) type.getClassifier()).getQualifiedName();
            return type.isMarkedNullable() ? name + "?" : name;
        }
    }

    @Test
    void auditsDeprecatedConstructorsOfAModule() {
        List<DeprecationAuditor.Finding> findings = DeprecationAuditor.audit(KotlinModule.class);

        assertThat(findings).extracting(DeprecationAuditor.Finding::signature).containsExactly(
                "<init>(kotlin.Int, kotlin.Boolean, kotlin.Boolean)",
                "<init>(kotlin.Int, kotlin.Boolean, kotlin.Boolean, kotlin.Boolean)",
                "<init>(kotlin.Int, kotlin.Boolean, kotlin.Boolean, kotlin.Boolean,"
                        + " com.fasterxml.jackson.module.kotlin.SingletonSupport, kotlin.Boolean)");
        assertThat(findings).extracting(DeprecationAuditor.Finding::level).containsExactly(
                DeprecationLevel.HIDDEN, DeprecationLevel.HIDDEN, DeprecationLevel.WARNING);
        assertThat(findings.get(0).message()).isEqualTo("For ABI compatibility");
        assertThat(findings.get(0).replacement()).isEmpty();
        assertThat(DeprecationAuditor.migrationPlan(KotlinModule.class)).containsOnlyKeys(findings.get(2).signature());

        DeprecationAuditor.Finding namedParameters = findings.get(2);
        assertThat(namedParameters.message()).isEqualTo("Use KotlinModule.Builder instead of named constructor parameters.");
        assertThat(namedParameters.replacement()).startsWith("KotlinModule.Builder()")
                .contains(".withReflectionCacheSize(reflectionCacheSize)",
                        ".configure(KotlinFeature.StrictNullChecks, strictNullChecks)")
                .endsWith(".build()");
        assertThat(namedParameters.imports()).containsExactly("com.fasterxml.jackson.module.kotlin.KotlinFeature");
    }

    @Test
    void plansMigrationOffDeprecatedBuilderMembers() {
        Map<String, String> plan = DeprecationAuditor.migrationPlan(KotlinModule.Builder.class);

        assertThat(plan).hasSize(11);
        assertThat(plan).containsEntry("reflectionCacheSize(kotlin.Int)", "withReflectionCacheSize(reflectionCacheSize)")
                .containsEntry("nullToEmptyCollection(kotlin.Boolean)",
                        "configure(KotlinFeature.NullToEmptyCollection, nullToEmptyCollection)")
                .containsEntry("getStrictNullChecks()", "isEnabled(KotlinFeature.StrictNullChecks)")
                .containsEntry("singletonSupport(com.fasterxml.jackson.module.kotlin.SingletonSupport)",
                        "configure(KotlinFeature.SingletonSupport, singletonSupport)");
        assertThat(plan).doesNotContainKeys("withReflectionCacheSize(kotlin.Int)", "build()",
                "isEnabled(com.fasterxml.jackson.module.kotlin.KotlinFeature)");

        List<DeprecationAuditor.Finding> findings = DeprecationAuditor.audit(KotlinModule.Builder.class);
        assertThat(findings).allSatisfy(finding -> assertThat(finding.level()).isEqualTo(DeprecationLevel.WARNING));
        assertThat(findings).filteredOn(finding -> finding.signature().equals("reflectionCacheSize(kotlin.Int)"))
                .singleElement().satisfies(finding -> assertThat(finding.imports()).isEmpty());
    }

    @Test
    void separatesHiddenFromErrorLevelDeprecations() {
        List<DeprecationAuditor.Finding> job = DeprecationAuditor.audit(Job.class);

        assertThat(job).extracting(DeprecationAuditor.Finding::signature)
                .containsExactly("cancel()", "cancel(kotlin.Throwable?)", "plus(kotlinx.coroutines.Job)");
        assertThat(job).extracting(DeprecationAuditor.Finding::level)
                .containsExactly(DeprecationLevel.HIDDEN, DeprecationLevel.HIDDEN, DeprecationLevel.ERROR);
        assertThat(job.get(0).message()).isEqualTo("Since 1.2.0, binary compatibility with versions <= 1.1.x");
        assertThat(job.get(2).message()).startsWith("Operator '+' on two Job objects is meaningless.");
        assertThat(DeprecationAuditor.migrationPlan(Job.class)).isEmpty();

        assertThat(DeprecationAuditor.audit(BroadcastChannel.class)).singleElement().satisfies(finding -> {
            assertThat(finding.signature()).isEqualTo("cancel(kotlin.Throwable?)");
            assertThat(finding.level()).isEqualTo(DeprecationLevel.HIDDEN);
            assertThat(finding.message()).isEqualTo("Binary compatibility only");
        });
    }

    @Test
    void decodesAnnotationsThroughTheirKotlinModel() {
        KClass<Deprecated> deprecated = JvmClassMappingKt.getKotlinClass(Deprecated.class);
        KFunction<?> plus = DeprecationAuditor.member(Job.class, "plus(kotlinx.coroutines.Job)");

        assertThat(DeprecationAuditor.annotationNames(plus)).containsExactly("Deprecated");
        KAnnotatedElement plusAnnotationAccess = plus;
        Map<String, Object> attributes = DeprecationAuditor.attributes(plusAnnotationAccess.getAnnotations().get(0));
        assertThat(attributes.keySet()).containsExactly("message", "replaceWith", "level");
        assertThat(attributes).containsEntry("level", "ERROR")
                .containsEntry("replaceWith", Map.of("expression", "", "imports", List.of()));

        assertThat(DeprecationAuditor.annotationNames(deprecated)).containsExactly("Target", "MustBeDocumented");
        List<Target> targets = KAnnotatedElements.findAnnotations(deprecated, JvmClassMappingKt.getKotlinClass(Target.class));
        assertThat(targets).singleElement().satisfies(target -> assertThat(DeprecationAuditor.attributes(target))
                .containsEntry("allowedTargets", List.of("CLASS", "FUNCTION", "PROPERTY", "ANNOTATION_CLASS",
                        "CONSTRUCTOR", "PROPERTY_SETTER", "PROPERTY_GETTER", "TYPEALIAS")));
        assertThat(targets.get(0).allowedTargets()).contains(AnnotationTarget.CONSTRUCTOR);
        assertThat(KAnnotatedElements.findAnnotations(deprecated, JvmClassMappingKt.getKotlinClass(MustBeDocumented.class)))
                .hasSize(1);

        KFunction<?> build = DeprecationAuditor.member(KotlinModule.Builder.class, "build()");
        KAnnotatedElement buildAnnotationAccess = build;
        assertThat(buildAnnotationAccess.getAnnotations()).isEmpty();
        assertThat(KAnnotatedElements.findAnnotations(build, deprecated)).isEmpty();
    }

    @Test
    void provesAReplacementEquivalentToTheDeprecatedMember() {
        KFunction<?> deprecatedSetter = DeprecationAuditor.member(KotlinModule.Builder.class,
                "nullToEmptyCollection(kotlin.Boolean)");
        KFunction<?> deprecatedGetter = DeprecationAuditor.member(KotlinModule.Builder.class, "getNullToEmptyCollection()");

        KotlinModule.Builder legacy = new KotlinModule.Builder();
        Object returned = deprecatedSetter.call(legacy, true);
        KotlinModule.Builder migrated = new KotlinModule.Builder().configure(KotlinFeature.NullToEmptyCollection, true);

        assertThat(returned).isSameAs(legacy);
        assertThat(deprecatedGetter.call(legacy)).isEqualTo(true);
        assertThat(legacy.isEnabled(KotlinFeature.NullToEmptyCollection)).isTrue();
        assertThat(migrated.isEnabled(KotlinFeature.NullToEmptyCollection)).isTrue();
        assertThat(deprecatedGetter.call(new KotlinModule.Builder())).isEqualTo(false);

        KotlinModule fromLegacy = legacy.build();
        KotlinModule fromMigrated = migrated.build();
        assertThat(fromLegacy.getNullToEmptyCollection()).isTrue();
        assertThat(fromMigrated.getNullToEmptyCollection()).isEqualTo(fromLegacy.getNullToEmptyCollection());
        assertThat(fromMigrated.getNullToEmptyMap()).isFalse();
    }
}
