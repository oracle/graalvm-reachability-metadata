/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.spi.component.ComponentConstructor;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentInjector;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProviderContext;
import java.lang.annotation.Annotation;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.ws.rs.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentConstructorTest {
    private static final InjectableProviderContext INJECTABLE_PROVIDER_CONTEXT =
            new TestInjectableProviderContext();

    @Test
    void getInstanceUsesDefaultConstructorAndInvokesPostConstructMethod() throws Exception {
        ComponentConstructor<DefaultConstructedComponent> constructor = componentConstructor(
                DefaultConstructedComponent.class);

        DefaultConstructedComponent component = constructor.getInstance();

        assertThat(component.createdWithDefaultConstructor).isTrue();
        assertThat(component.postConstructInvoked).isTrue();
    }

    @Test
    void getInstanceUsesAnnotatedConstructorWithInjectableParameter() throws Exception {
        ComponentConstructor<ContextConstructedComponent> constructor = componentConstructor(
                ContextConstructedComponent.class);

        ContextConstructedComponent component = constructor.getInstance();

        assertThat(component.contextValue).isEqualTo("injected context value");
    }

    private static <T> ComponentConstructor<T> componentConstructor(Class<T> componentClass) {
        return new ComponentConstructor<>(
                INJECTABLE_PROVIDER_CONTEXT,
                componentClass,
                new NoOpComponentInjector<>(INJECTABLE_PROVIDER_CONTEXT, componentClass));
    }

    public static class DefaultConstructedComponent {
        private final boolean createdWithDefaultConstructor;
        private boolean postConstructInvoked;

        public DefaultConstructedComponent() {
            this.createdWithDefaultConstructor = true;
        }

        @PostConstruct
        public void initialize() {
            this.postConstructInvoked = true;
        }
    }

    public static class ContextConstructedComponent {
        private final String contextValue;

        public ContextConstructedComponent(@Context String contextValue) {
            this.contextValue = contextValue;
        }
    }

    private static final class NoOpComponentInjector<T> extends ComponentInjector<T> {
        private NoOpComponentInjector(
                InjectableProviderContext injectableProviderContext,
                Class<T> componentClass) {
            super(injectableProviderContext, componentClass);
        }

        @Override
        public void inject(T component) {
            // Constructor selection is the behavior under test.
            // Field and setter injection stay out of scope.
        }
    }

    private static final class TestInjectableProviderContext implements InjectableProviderContext {
        @Override
        public boolean isAnnotationRegistered(
                Class<? extends Annotation> annotationClass,
                Class<?> contextClass) {
            return false;
        }

        @Override
        public boolean isInjectableProviderRegistered(
                Class<? extends Annotation> annotationClass,
                Class<?> contextClass,
                ComponentScope scope) {
            return annotationClass == Context.class;
        }

        @Override
        public <A extends Annotation, C> Injectable getInjectable(
                Class<? extends Annotation> annotationClass,
                ComponentContext injectableContext,
                A annotation,
                C context,
                ComponentScope scope) {
            if (annotationClass == Context.class) {
                return new ConstantInjectable("injected context value");
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
            if (annotationClass == Context.class) {
                return new ConstantInjectable("injected context value");
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
            if (annotationClass == Context.class) {
                return new InjectableScopePair(
                        new ConstantInjectable("injected context value"),
                        ComponentScope.Undefined);
            }
            return null;
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
