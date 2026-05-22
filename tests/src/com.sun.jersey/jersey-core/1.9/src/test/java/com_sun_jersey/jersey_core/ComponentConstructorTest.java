/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentConstructor;
import com.sun.jersey.core.spi.component.ComponentInjector;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProviderContext;
import com.sun.jersey.spi.inject.InjectableProviderContext.InjectableScopePair;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import javax.annotation.PostConstruct;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentConstructorTest {
    @Test
    public void createsNoArgumentComponentAndInvokesPostConstruct() throws Exception {
        final SimpleInjectableProviderContext injectableProviderContext = new SimpleInjectableProviderContext();
        final ComponentInjector<PostConstructComponent> componentInjector = new NoOpComponentInjector<>(
                injectableProviderContext,
                PostConstructComponent.class);
        final ComponentConstructor<PostConstructComponent> componentConstructor = new ComponentConstructor<>(
                injectableProviderContext,
                PostConstructComponent.class,
                componentInjector);

        final PostConstructComponent component = componentConstructor.getInstance();

        assertThat(component.isInitialized()).isTrue();
    }

    @Test
    public void createsComponentUsingInjectableConstructorParameter() throws Exception {
        final SimpleInjectableProviderContext injectableProviderContext = new SimpleInjectableProviderContext();
        final ComponentInjector<ConstructorInjectedComponent> componentInjector = new NoOpComponentInjector<>(
                injectableProviderContext,
                ConstructorInjectedComponent.class);
        final ComponentConstructor<ConstructorInjectedComponent> componentConstructor = new ComponentConstructor<>(
                injectableProviderContext,
                ConstructorInjectedComponent.class,
                componentInjector);

        final ConstructorInjectedComponent component = componentConstructor.getInstance();

        assertThat(component.getValue()).isEqualTo(SimpleInjectableProviderContext.INJECTED_VALUE);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface TestInjection {
    }

    public static class PostConstructComponent {
        private boolean initialized;

        public PostConstructComponent() {
        }

        @PostConstruct
        public void initialize() {
            initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    public static class ConstructorInjectedComponent {
        private final String value;

        public ConstructorInjectedComponent(@TestInjection String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static final class NoOpComponentInjector<T> extends ComponentInjector<T> {
        private NoOpComponentInjector(InjectableProviderContext injectableProviderContext, Class<T> componentClass) {
            super(injectableProviderContext, componentClass);
        }

        @Override
        public void inject(T component) {
        }
    }

    private static final class SimpleInjectableProviderContext implements InjectableProviderContext {
        private static final String INJECTED_VALUE = "component-constructor-value";

        @Override
        public boolean isAnnotationRegistered(Class<? extends Annotation> annotationClass, Class<?> contextClass) {
            return annotationClass == TestInjection.class;
        }

        @Override
        public boolean isInjectableProviderRegistered(
                Class<? extends Annotation> annotationClass,
                Class<?> contextClass,
                ComponentScope scope) {
            return annotationClass == TestInjection.class;
        }

        @Override
        public <A extends Annotation, C> Injectable getInjectable(
                Class<? extends Annotation> annotationClass,
                ComponentContext injectableContext,
                A annotation,
                C context,
                ComponentScope scope) {
            if (annotationClass == TestInjection.class) {
                return new ConstantInjectable(INJECTED_VALUE);
            }
            return null;
        }

        @Override
        public <A extends Annotation, C> Injectable getInjectable(
                Class<? extends Annotation> annotationClass,
                ComponentContext injectableContext,
                A annotation,
                C context,
                List<ComponentScope> scopes) {
            if (annotationClass == TestInjection.class) {
                return new ConstantInjectable(INJECTED_VALUE);
            }
            return null;
        }

        @Override
        public <A extends Annotation, C> InjectableScopePair getInjectableWithScope(
                Class<? extends Annotation> annotationClass,
                ComponentContext injectableContext,
                A annotation,
                C context,
                List<ComponentScope> scopes) {
            final Injectable injectable = getInjectable(annotationClass, injectableContext, annotation, context, scopes);
            if (injectable == null) {
                return null;
            }
            return new InjectableScopePair(injectable, ComponentScope.Undefined);
        }
    }

    private static final class ConstantInjectable implements Injectable<String> {
        private final String value;

        private ConstantInjectable(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
