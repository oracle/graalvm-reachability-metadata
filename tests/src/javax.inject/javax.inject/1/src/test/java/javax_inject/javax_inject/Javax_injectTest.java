/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_inject.javax_inject;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Javax_injectTest {
    @Test
    void providerSupportsDeferredLookupAndDifferentImplementations() {
        CountingProvider countingProvider = new CountingProvider();
        GreetingService service = new GreetingService(countingProvider);
        Provider<String> fixedProvider = () -> "fixed";

        assertThat(countingProvider.get()).isEqualTo("value-1");
        assertThat(service.greet("hello")).isEqualTo("hello value-2");
        assertThat(service.greet("welcome")).isEqualTo("welcome value-3");
        assertThat(fixedProvider.get()).isEqualTo("fixed");
    }

    @Test
    void injectAndNamedAnnotationsAreVisibleOnInjectionPoints() throws NoSuchFieldException, NoSuchMethodException {
        Constructor<InjectionFixture> constructor = InjectionFixture.class.getDeclaredConstructor(String.class);
        Field field = InjectionFixture.class.getDeclaredField("fieldProvider");
        Method method = InjectionFixture.class.getDeclaredMethod("configure", String.class);
        Named unnamed = getSingleAnnotation(InjectionFixture.class.getDeclaredField("unnamedDependency"), Named.class);
        Named constructorName = constructor.getParameters()[0].getAnnotation(Named.class);
        Named fieldName = getSingleAnnotation(field, Named.class);
        Named methodName = method.getParameters()[0].getAnnotation(Named.class);

        assertThat(getSingleAnnotation(constructor, Inject.class)).isNotNull();
        assertThat(getSingleAnnotation(field, Inject.class)).isNotNull();
        assertThat(getSingleAnnotation(method, Inject.class)).isNotNull();
        assertThat(unnamed).isNotNull();
        assertThat(unnamed.value()).isEmpty();
        assertThat(constructorName.value()).isEqualTo("constructor");
        assertThat(fieldName.value()).isEqualTo("field");
        assertThat(methodName.value()).isEqualTo("suffix");

        InjectionFixture fixture = new InjectionFixture("constructor");
        fixture.fieldProvider = () -> "field";
        fixture.configure("method");

        assertThat(fixture.describe()).isEqualTo("constructor-method-field");
    }

    @Test
    void qualifierAndScopeSupportCustomRuntimeAnnotations() throws NoSuchFieldException {
        Remote remote = getSingleAnnotation(CustomBindingFixture.class.getDeclaredField("paymentsProvider"), Remote.class);

        assertThat(getSingleAnnotation(Remote.class, Qualifier.class)).isNotNull();
        assertThat(getSingleAnnotation(RequestScoped.class, Scope.class)).isNotNull();
        assertThat(getSingleAnnotation(RequestScopedComponent.class, RequestScoped.class)).isNotNull();
        assertThat(remote).isNotNull();
        assertThat(remote.value()).isEqualTo("payments");
    }

    @Test
    void builtInAnnotationsExposeTheExpectedMetaAnnotationContracts() {
        assertDocumentedRuntimeAnnotation(Inject.class);
        assertThat(getSingleAnnotation(Inject.class, Target.class).value())
                .containsExactly(ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD);

        assertDocumentedRuntimeAnnotation(Named.class);
        assertThat(getSingleAnnotation(Named.class, Qualifier.class)).isNotNull();
        assertThat(getSingleAnnotation(Named.class, Target.class)).isNull();

        assertDocumentedRuntimeAnnotation(Qualifier.class);
        assertThat(getSingleAnnotation(Qualifier.class, Target.class).value())
                .containsExactly(ElementType.ANNOTATION_TYPE);

        assertDocumentedRuntimeAnnotation(Scope.class);
        assertThat(getSingleAnnotation(Scope.class, Target.class).value())
                .containsExactly(ElementType.ANNOTATION_TYPE);

        assertDocumentedRuntimeAnnotation(Singleton.class);
        assertThat(getSingleAnnotation(Singleton.class, Scope.class)).isNotNull();
        assertThat(getSingleAnnotation(Singleton.class, Target.class)).isNull();
        assertThat(getSingleAnnotation(SingletonComponent.class, Singleton.class)).isNotNull();
    }

    private static void assertDocumentedRuntimeAnnotation(Class<? extends Annotation> annotationType) {
        Retention retention = getSingleAnnotation(annotationType, Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(hasAnnotation(annotationType, Documented.class)).isTrue();
    }

    private static boolean hasAnnotation(
            AnnotatedElement annotatedElement,
            Class<? extends Annotation> annotationType) {
        return annotatedElement.getAnnotationsByType(annotationType).length > 0;
    }

    private static <A extends Annotation> A getSingleAnnotation(
            AnnotatedElement annotatedElement,
            Class<A> annotationType) {
        A[] annotations = annotatedElement.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static final class GreetingService {
        private final Provider<String> provider;

        private GreetingService(Provider<String> provider) {
            this.provider = provider;
        }

        private String greet(String prefix) {
            return prefix + " " + provider.get();
        }
    }

    private static final class CountingProvider implements Provider<String> {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public String get() {
            return "value-" + counter.incrementAndGet();
        }
    }

    private static final class InjectionFixture {
        @Named
        private String unnamedDependency;

        @Inject
        @Named("field")
        private Provider<String> fieldProvider;

        private final String constructorValue;
        private String suffix;

        @Inject
        private InjectionFixture(@Named("constructor") String constructorValue) {
            this.constructorValue = constructorValue;
        }

        @Inject
        private void configure(@Named("suffix") String suffix) {
            this.suffix = suffix;
        }

        private String describe() {
            return constructorValue + "-" + suffix + "-" + fieldProvider.get();
        }
    }

    @Qualifier
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    private @interface Remote {
        String value();
    }

    @Scope
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    private @interface RequestScoped {
    }

    private static final class CustomBindingFixture {
        @Remote("payments")
        private Provider<String> paymentsProvider;
    }

    @RequestScoped
    private static final class RequestScopedComponent {
    }

    @Singleton
    private static final class SingletonComponent {
    }
}
