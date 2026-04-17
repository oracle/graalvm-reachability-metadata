/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_inject.jakarta_inject_api;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

class Jakarta_inject_apiTest {
    @Test
    void publicTypesAndManualAnnotationImplementationsAreUsable() {
        Named named = new Named() {
            @Override
            public String value() {
                return "primary";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Named.class;
            }
        };
        Inject inject = new Inject() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Inject.class;
            }
        };
        Qualifier qualifier = new Qualifier() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Qualifier.class;
            }
        };
        Scope scope = new Scope() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Scope.class;
            }
        };
        Singleton singleton = new Singleton() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Singleton.class;
            }
        };
        Provider<List<String>> tokenProvider = new Provider<>() {
            @Override
            public List<String> get() {
                return List.of("jakarta", "inject", named.value());
            }
        };

        assertThat(List.of(Inject.class, Named.class, Qualifier.class, Scope.class, Singleton.class, Provider.class))
                .containsExactly(Inject.class, Named.class, Qualifier.class, Scope.class, Singleton.class, Provider.class);
        assertThat(named.value()).isEqualTo("primary");
        assertThat(named.annotationType()).isSameAs(Named.class);
        assertThat(inject.annotationType()).isSameAs(Inject.class);
        assertThat(qualifier.annotationType()).isSameAs(Qualifier.class);
        assertThat(scope.annotationType()).isSameAs(Scope.class);
        assertThat(singleton.annotationType()).isSameAs(Singleton.class);
        assertThat(tokenProvider.get()).containsExactly("jakarta", "inject", "primary");
    }

    @Test
    void providerBackedAnnotatedComponentsExecuteNormally() {
        AtomicInteger prefixSequence = new AtomicInteger();
        Provider<String> prefixProvider = () -> "prefix-" + prefixSequence.incrementAndGet();
        Provider<MessageTemplate> templateProvider = () -> new MessageTemplate("Hello");
        Provider<String> suffixProvider = () -> "!";

        GreetingService greetingService = new GreetingService(prefixProvider, templateProvider);
        greetingService.defaultAudience = "team";
        greetingService.configure(suffixProvider, " ");

        assertThat(greetingService.greet(null)).isEqualTo("prefix-1 Hello, team!");
        assertThat(greetingService.greet("native image")).isEqualTo("prefix-2 Hello, native image!");
        assertThat(greetingService.greet("  metadata  ")).isEqualTo("prefix-3 Hello, metadata!");
    }

    @Test
    void runtimeVisibleAnnotationsRetainValuesAcrossInjectionPointsAndMetaAnnotations() throws Exception {
        Constructor<GreetingService> constructor = GreetingService.class.getDeclaredConstructor(Provider.class, Provider.class);
        Field defaultAudienceField = GreetingService.class.getDeclaredField("defaultAudience");
        Method configureMethod = GreetingService.class.getDeclaredMethod("configure", Provider.class, String.class);

        assertThat(constructor.getAnnotation(Inject.class)).isNotNull();
        assertThat(constructor.getParameters()[0].getAnnotation(Named.class).value()).isEqualTo("prefix");
        assertThat(constructor.getParameters()[1].getAnnotation(Remote.class)).isNotNull();

        assertThat(defaultAudienceField.getAnnotation(Inject.class)).isNotNull();
        assertThat(defaultAudienceField.getAnnotation(Named.class).value()).isEmpty();

        assertThat(configureMethod.getAnnotation(Inject.class)).isNotNull();
        assertThat(configureMethod.getParameters()[0].getAnnotation(Named.class).value()).isEqualTo("suffix");
        assertThat(configureMethod.getParameters()[1].getAnnotation(Named.class).value()).isEqualTo("separator");

        assertThat(Remote.class.getAnnotation(Qualifier.class)).isNotNull();
        assertThat(RequestScoped.class.getAnnotation(Scope.class)).isNotNull();
        assertThat(MessageTemplate.class.getAnnotation(Singleton.class)).isNotNull();
    }

    @Test
    void builtInAnnotationsExposeQualifierScopeDefaultsAndSupportedTargets() throws Exception {
        Method namedValueMethod = Named.class.getDeclaredMethod("value");

        assertThat(Named.class.getAnnotation(Qualifier.class)).isNotNull();
        assertThat(namedValueMethod.getDefaultValue()).isEqualTo("");

        assertThat(Singleton.class.getAnnotation(Scope.class)).isNotNull();

        assertThat(Inject.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(CONSTRUCTOR, METHOD, FIELD);
        assertThat(Qualifier.class.getAnnotation(Target.class).value()).containsExactly(ANNOTATION_TYPE);
        assertThat(Scope.class.getAnnotation(Target.class).value()).containsExactly(ANNOTATION_TYPE);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER, TYPE})
    private @interface Remote {
    }

    @Scope
    @Retention(RetentionPolicy.RUNTIME)
    @Target(TYPE)
    private @interface RequestScoped {
    }

    @Singleton
    private static final class MessageTemplate {
        private final String salutation;

        private MessageTemplate(String salutation) {
            this.salutation = salutation;
        }

        private String render(String audience) {
            return salutation + ", " + audience;
        }
    }

    @RequestScoped
    private static final class GreetingService {
        private final Provider<String> prefixProvider;
        private final Provider<MessageTemplate> templateProvider;

        @Inject
        @Named
        private String defaultAudience;

        private Provider<String> suffixProvider;
        private String separator;

        @Inject
        private GreetingService(
                @Named("prefix") Provider<String> prefixProvider,
                @Remote Provider<MessageTemplate> templateProvider) {
            this.prefixProvider = prefixProvider;
            this.templateProvider = templateProvider;
        }

        @Inject
        private void configure(@Named("suffix") Provider<String> suffixProvider, @Named("separator") String separator) {
            this.suffixProvider = suffixProvider;
            this.separator = separator;
        }

        private String greet(String audience) {
            String resolvedAudience = audience == null || audience.isBlank() ? defaultAudience : audience.trim();
            return prefixProvider.get() + separator + templateProvider.get().render(resolvedAudience) + suffixProvider.get();
        }
    }
}
