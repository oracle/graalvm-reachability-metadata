/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.cglib.core.DuplicatesPredicate;
import com.google.inject.internal.cglib.core.MethodWrapper;
import com.google.inject.matcher.Matchers;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

public class MethodWrapperTest {
    @Test
    void createsStableKeysForEquivalentMethodSignatures() throws NoSuchMethodException {
        Method first = FirstComparableService.class.getMethod("describe", String.class);
        Method second = SecondComparableService.class.getMethod("describe", String.class);

        Set<?> wrappedMethods = MethodWrapper.createSet(Arrays.asList(first, second));

        assertThat(wrappedMethods).hasSize(1);
        assertThat(MethodWrapper.create(first)).isEqualTo(MethodWrapper.create(second));
    }

    @Test
    void cglibDuplicateFilteringRecognizesPreviouslySeenMethodSignatures()
            throws NoSuchMethodException {
        Method first = FirstComparableService.class.getMethod("describe", String.class);
        Method second = SecondComparableService.class.getMethod("describe", String.class);
        DuplicatesPredicate predicate = new DuplicatesPredicate();

        assertThat(predicate.evaluate(first)).isTrue();
        assertThat(predicate.evaluate(second)).isFalse();
    }

    @Test
    void guiceAopInterceptorsWrapAnnotatedConcreteMethods() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindInterceptor(
                        Matchers.subclassesOf(Greeter.class),
                        Matchers.annotatedWith(Intercepted.class),
                        new PrefixingInterceptor());
            }
        });

        Greeter greeter = injector.getInstance(Greeter.class);

        assertThat(greeter.greet("Sisu")).isEqualTo("intercepted hello Sisu");
        assertThat(greeter.plain("Guice")).isEqualTo("plain Guice");
    }

    public static class FirstComparableService {
        public String describe(String value) {
            return "first " + value;
        }
    }

    public static class SecondComparableService {
        public String describe(String value) {
            return "second " + value;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Intercepted {
    }

    public static class Greeter {
        @Intercepted
        public String greet(String name) {
            return "hello " + name;
        }

        public String plain(String name) {
            return "plain " + name;
        }
    }

    private static final class PrefixingInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            return "intercepted " + invocation.proceed();
        }
    }
}
