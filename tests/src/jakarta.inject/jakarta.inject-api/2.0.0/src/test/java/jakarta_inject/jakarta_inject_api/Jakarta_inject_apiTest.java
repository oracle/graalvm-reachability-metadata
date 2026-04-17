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

        assertThat(constructor.getAnnotationsByType(Inject.class)).isNotEmpty();
        assertThat(constructor.getParameters()[0].getAnnotationsByType(Named.class)[0].value()).isEqualTo("prefix");
        assertThat(constructor.getParameters()[1].getAnnotationsByType(Remote.class)).isNotEmpty();

        assertThat(defaultAudienceField.getAnnotationsByType(Inject.class)).isNotEmpty();
        assertThat(defaultAudienceField.getAnnotationsByType(Named.class)[0].value()).isEmpty();

        assertThat(configureMethod.getAnnotationsByType(Inject.class)).isNotEmpty();
        assertThat(configureMethod.getParameters()[0].getAnnotationsByType(Named.class)[0].value()).isEqualTo("suffix");
        assertThat(configureMethod.getParameters()[1].getAnnotationsByType(Named.class)[0].value()).isEqualTo("separator");

        assertThat(Remote.class.getAnnotationsByType(Qualifier.class)).isNotEmpty();
        assertThat(RequestScoped.class.getAnnotationsByType(Scope.class)).isNotEmpty();
        assertThat(MessageTemplate.class.getAnnotationsByType(Singleton.class)).isNotEmpty();
    }

    @Test
    void builtInAnnotationsExposeQualifierScopeDefaultsAndSupportedTargets() throws Exception {
        Method namedValueMethod = Named.class.getDeclaredMethod("value");

        assertThat(Named.class.getAnnotationsByType(Qualifier.class)).isNotEmpty();
        assertThat(namedValueMethod.getDefaultValue()).isEqualTo("");

        assertThat(Singleton.class.getAnnotationsByType(Scope.class)).isNotEmpty();

        assertThat(Inject.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactlyInAnyOrder(CONSTRUCTOR, METHOD, FIELD);
        assertThat(Qualifier.class.getAnnotationsByType(Target.class)[0].value()).containsExactly(ANNOTATION_TYPE);
        assertThat(Scope.class.getAnnotationsByType(Target.class)[0].value()).containsExactly(ANNOTATION_TYPE);
    }

    @Test
    void nestedProvidersCanSwitchLookupStrategiesWithoutRecreatingConsumers() {
        AtomicInteger greetingStyle = new AtomicInteger();
        Provider<String> formalGreetingProvider = () -> "Good day";
        Provider<String> casualGreetingProvider = () -> "Hi";
        Provider<Provider<String>> greetingProvider = () -> greetingStyle.get() == 0
                ? formalGreetingProvider
                : casualGreetingProvider;

        DeferredGreeter deferredGreeter = new DeferredGreeter(greetingProvider);

        assertThat(deferredGreeter.greet("team")).isEqualTo("Good day, team");

        greetingStyle.set(1);
        assertThat(deferredGreeter.greet("native image")).isEqualTo("Hi, native image");

        greetingStyle.set(0);
        assertThat(deferredGreeter.greet("metadata")).isEqualTo("Good day, metadata");
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

    private static final class DeferredGreeter {
        private final Provider<Provider<String>> greetingProvider;

        private DeferredGreeter(Provider<Provider<String>> greetingProvider) {
            this.greetingProvider = greetingProvider;
        }

        private String greet(String audience) {
            return greetingProvider.get().get() + ", " + audience;
        }
    }
}
