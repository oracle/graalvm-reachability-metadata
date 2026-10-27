/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Unit;
import kotlin.coroutines.ContinuationInterceptor;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.random.Random;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KVisibility;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlin.text.Regex;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.CoroutineExceptionHandler;
import kotlinx.coroutines.CoroutineName;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobKt;
import kotlinx.coroutines.NonCancellable;
import org.junit.jupiter.api.Test;

/**
 * Object and companion-object reflection: a singleton locator resolves Kotlin `object`
 * declarations through `KClass.objectInstance` and companion objects through
 * `companionObject`/`companionObjectInstance`, then calls companion factories by parameter name.
 *
 * The locator is configured with classes only. It never reads a static `INSTANCE` or `Companion`
 * field: the Kotlin model says which classes are singletons, which carry a companion, and which
 * companion functions accept the named arguments a caller supplies. The same companion lookup
 * turns a coroutine-context element type into the `CoroutineContext.Key` that reads it.
 */
class SingletonLocatorTest {

    /** Locates application singletons declared as Kotlin objects and companions. */
    static final class SingletonLocator {

        /** The instance of the `object` declaration `type`; fails for an ordinary class. */
        static Object singleton(Class<?> type) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            Object instance = kClass.getObjectInstance();
            if (instance == null) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " is not an object declaration");
            }
            return instance;
        }

        /** The companion object of `type`; fails when the class declares none. */
        static Object companion(Class<?> type) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            Object companion = KClasses.getCompanionObjectInstance(kClass);
            if (companion == null) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " has no companion object");
            }
            return companion;
        }

        /**
         * Calls the companion function `name` of `owner` whose value parameters are exactly the
         * keys of `arguments`, binding the companion as receiver and each argument by name.
         */
        static Object callFactory(Class<?> owner, String name, Map<String, Object> arguments) {
            KClass<?> companionClass = KClasses.getCompanionObject(JvmClassMappingKt.getKotlinClass(owner));
            if (companionClass == null) {
                throw new IllegalArgumentException(owner.getName() + " has no companion object");
            }
            for (KFunction<?> function : KClasses.getMemberFunctions(companionClass)) {
                if (!function.getName().equals(name) || !parameterNames(function).equals(arguments.keySet())) {
                    continue;
                }
                Map<KParameter, Object> bound = new HashMap<>();
                bound.put(KCallables.getInstanceParameter(function), companion(owner));
                for (KParameter parameter : KCallables.getValueParameters(function)) {
                    bound.put(parameter, arguments.get(parameter.getName()));
                }
                return function.callBy(bound);
            }
            throw new IllegalArgumentException("no factory " + name + arguments.keySet() + " on "
                    + companionClass.getQualifiedName());
        }

        /** The element of `context` registered under the companion key of `elementType`. */
        static CoroutineContext.Element element(CoroutineContext context, Class<?> elementType) {
            Object key = companion(elementType);
            if (!(key instanceof CoroutineContext.Key<?> contextKey)) {
                throw new IllegalArgumentException(elementType.getName() + " companion is not a context key");
            }
            return context.get(contextKey);
        }

        private static Set<String> parameterNames(KFunction<?> function) {
            Set<String> names = new HashSet<>();
            for (KParameter parameter : KCallables.getValueParameters(function)) {
                names.add(parameter.getName());
            }
            return names;
        }
    }

    @Test
    void resolvesObjectDeclarations() {
        assertThat(SingletonLocator.singleton(Unit.class)).isSameAs(Unit.INSTANCE);
        assertThat(SingletonLocator.singleton(EmptyCoroutineContext.class)).isSameAs(EmptyCoroutineContext.INSTANCE);
        assertThat(SingletonLocator.singleton(Dispatchers.class)).isSameAs(Dispatchers.INSTANCE);
        assertThat(SingletonLocator.singleton(GlobalScope.class)).isSameAs(GlobalScope.INSTANCE);
        assertThat(SingletonLocator.singleton(NonCancellable.class)).isSameAs(NonCancellable.INSTANCE);

        assertThatThrownBy(() -> SingletonLocator.singleton(Regex.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.text.Regex is not an object declaration");
        assertThatThrownBy(() -> SingletonLocator.singleton(Random.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.random.Random is not an object declaration");
    }

    @Test
    void describesCompanionObjects() {
        KClass<Random> random = JvmClassMappingKt.getKotlinClass(Random.class);
        KClass<?> randomCompanion = KClasses.getCompanionObject(random);

        assertThat(random.isAbstract()).isTrue();
        assertThat(random.getVisibility()).isEqualTo(KVisibility.PUBLIC);
        assertThat(randomCompanion).isNotNull();
        assertThat(randomCompanion.isCompanion()).isTrue();
        assertThat(randomCompanion.getSimpleName()).isEqualTo("Default");
        assertThat(randomCompanion.getObjectInstance()).isSameAs(Random.Default);
        assertThat(random.getNestedClasses()).contains(randomCompanion);
        assertThat(SingletonLocator.companion(Random.class)).isSameAs(Random.Default);

        KClass<?> regexCompanion = KClasses.getCompanionObject(JvmClassMappingKt.getKotlinClass(Regex.class));
        assertThat(regexCompanion.getQualifiedName()).isEqualTo("kotlin.text.Regex.Companion");
        assertThat(SingletonLocator.companion(Regex.class)).isSameAs(Regex.Companion);

        assertThat(KClasses.getCompanionObject(JvmClassMappingKt.getKotlinClass(Unit.class))).isNull();
        assertThatThrownBy(() -> SingletonLocator.companion(Unit.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.Unit has no companion object");
    }

    @Test
    void callsCompanionFactoriesByParameterName() {
        Object escaped = SingletonLocator.callFactory(Regex.class, "escape", Map.of("literal", "v1.2"));
        assertThat(escaped).isEqualTo("\\Qv1.2\\E");

        Object literal = SingletonLocator.callFactory(Regex.class, "fromLiteral", Map.of("literal", "1+1"));
        assertThat(literal).isInstanceOf(Regex.class);
        Regex pattern = (Regex) literal;
        assertThat(pattern.matches("1+1")).isTrue();
        assertThat(pattern.matches("11")).isFalse();

        Object bounded = SingletonLocator.callFactory(Random.class, "nextInt", Map.of("from", 7, "until", 8));
        assertThat(bounded).isEqualTo(7);
        Object below = SingletonLocator.callFactory(Random.class, "nextInt", Map.of("until", 1));
        assertThat(below).isEqualTo(0);

        assertThatThrownBy(() -> SingletonLocator.callFactory(Regex.class, "escape", Map.of("text", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no factory escape[text] on kotlin.text.Regex.Companion");
        assertThatThrownBy(() -> SingletonLocator.callFactory(Unit.class, "of", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.Unit has no companion object");
    }

    @Test
    void readsCoroutineContextElementsThroughCompanionKeys() {
        CompletableJob job = JobKt.Job(null);
        CoroutineName name = new CoroutineName("indexer");
        CoroutineContext context = name.plus(Dispatchers.getIO()).plus(job);

        assertThat(SingletonLocator.element(context, CoroutineName.class)).isSameAs(name);
        assertThat(SingletonLocator.element(context, Job.class)).isSameAs(job);
        assertThat(SingletonLocator.element(context, ContinuationInterceptor.class)).isSameAs(Dispatchers.getIO());
        assertThat(SingletonLocator.element(context, CoroutineExceptionHandler.class)).isNull();
        assertThat(((CoroutineName) SingletonLocator.element(context, CoroutineName.class)).getName())
                .isEqualTo("indexer");

        assertThatThrownBy(() -> SingletonLocator.element(context, Regex.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.text.Regex companion is not a context key");

        job.complete();
        assertThat(job.isCompleted()).isTrue();
        assertThat(List.of(job.isActive(), job.isCancelled())).containsExactly(false, false);
    }
}
