/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static java.lang.annotation.ElementType.PARAMETER;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.spi.component.ComponentConstructor;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentInjector;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProviderContext;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import javax.annotation.PostConstruct;
import org.junit.jupiter.api.Test;

public class ComponentConstructorTest {
    @Test
    void createsPublicComponentWithDefaultConstructorAndInvokesPostConstruct() throws Exception {
        final ComponentConstructor<PostConstructComponent> constructor = constructorFor(PostConstructComponent.class);

        final PostConstructComponent component = constructor.getInstance();

        assertThat(component.defaultConstructorInvoked).isTrue();
        assertThat(component.postConstructInvoked).isTrue();
    }

    @Test
    void createsPublicComponentWithInjectedConstructorArgument() throws Exception {
        final StubInjectableProviderContext context = new StubInjectableProviderContext("injected-value");
        final ComponentConstructor<InjectedConstructorComponent> constructor = constructorFor(
                context,
                InjectedConstructorComponent.class);

        final InjectedConstructorComponent component = constructor.getInstance();

        assertThat(component.value).isEqualTo("injected-value");
        assertThat(context.requestedAnnotationType).isEqualTo(ParameterValue.class);
    }

    private static <T> ComponentConstructor<T> constructorFor(Class<T> componentType) {
        return constructorFor(new StubInjectableProviderContext(null), componentType);
    }

    private static <T> ComponentConstructor<T> constructorFor(
            InjectableProviderContext context,
            Class<T> componentType) {
        final ComponentInjector<T> injector = new NoOpComponentInjector<T>(context, componentType);
        return new ComponentConstructor<T>(context, componentType, injector);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(PARAMETER)
    public @interface ParameterValue {
    }

    public static class PostConstructComponent {
        private final boolean defaultConstructorInvoked;
        private boolean postConstructInvoked;

        public PostConstructComponent() {
            this.defaultConstructorInvoked = true;
        }

        @PostConstruct
        public void markInitialized() {
            this.postConstructInvoked = true;
        }
    }

    public static class InjectedConstructorComponent {
        private final String value;

        public InjectedConstructorComponent(@ParameterValue String value) {
            this.value = value;
        }
    }

    private static class NoOpComponentInjector<T> extends ComponentInjector<T> {
        NoOpComponentInjector(InjectableProviderContext context, Class<T> componentType) {
            super(context, componentType);
        }

        @Override
        public void inject(T t) {
        }
    }

    private static class StubInjectableProviderContext implements InjectableProviderContext {
        private final Object injectableValue;
        private Class<? extends Annotation> requestedAnnotationType;

        StubInjectableProviderContext(Object injectableValue) {
            this.injectableValue = injectableValue;
        }

        @Override
        public boolean isAnnotationRegistered(Class<? extends Annotation> ac, Class<?> cc) {
            return false;
        }

        @Override
        public boolean isInjectableProviderRegistered(
                Class<? extends Annotation> ac,
                Class<?> cc,
                ComponentScope s) {
            return false;
        }

        @Override
        public <A extends Annotation, C> Injectable getInjectable(
                Class<? extends Annotation> ac,
                ComponentContext ic,
                A a,
                C c,
                ComponentScope s) {
            requestedAnnotationType = ac;
            if (ac == ParameterValue.class) {
                return new FixedInjectable(injectableValue);
            }
            return null;
        }

        @Override
        public <A extends Annotation, C> Injectable getInjectable(
                Class<? extends Annotation> ac,
                ComponentContext ic,
                A a,
                C c,
                List<ComponentScope> ls) {
            return getInjectable(ac, ic, a, c, ComponentScope.Undefined);
        }

        @Override
        public <A extends Annotation, C> InjectableScopePair getInjectableWithScope(
                Class<? extends Annotation> ac,
                ComponentContext ic,
                A a,
                C c,
                List<ComponentScope> ls) {
            final Injectable injectable = getInjectable(ac, ic, a, c, ComponentScope.Undefined);
            if (injectable == null) {
                return null;
            }
            return new InjectableScopePair(injectable, ComponentScope.Undefined);
        }
    }

    private static class FixedInjectable implements Injectable<Object> {
        private final Object value;

        FixedInjectable(Object value) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }
}
