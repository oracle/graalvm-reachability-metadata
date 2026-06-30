/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentInjector;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProviderContext;
import java.lang.annotation.Annotation;
import java.util.List;
import javax.ws.rs.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentInjectorTest {
    @Test
    void injectAssignsContextAnnotatedFieldsAndSetterMethods() {
        InjectableProviderContext providerContext = new TestInjectableProviderContext();
        ComponentInjector<InjectableComponent> injector = new ComponentInjector<>(
                providerContext,
                InjectableComponent.class);
        InjectableComponent component = new InjectableComponent();

        injector.inject(component);

        assertThat(component.fieldValue()).isEqualTo("field context value");
        assertThat(component.setterValue()).isEqualTo(42);
    }

    public static class InjectableComponent {
        @Context
        private String fieldValue;
        private Integer setterValue;

        public String fieldValue() {
            return fieldValue;
        }

        public Integer setterValue() {
            return setterValue;
        }

        @Context
        public void setSetterValue(Integer setterValue) {
            this.setterValue = setterValue;
        }
    }

    private static final class TestInjectableProviderContext implements InjectableProviderContext {
        @Override
        public boolean isAnnotationRegistered(
                Class<? extends Annotation> annotationClass,
                Class<?> contextClass) {
            return annotationClass == Context.class;
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
            if (annotationClass == Context.class && context == String.class) {
                return new ConstantInjectable<>("field context value");
            }
            if (annotationClass == Context.class && context == Integer.class) {
                return new ConstantInjectable<>(42);
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
            return getInjectable(
                    annotationClass,
                    injectableContext,
                    annotation,
                    context,
                    ComponentScope.Undefined);
        }

        @Override
        public <A extends Annotation, C> InjectableScopePair getInjectableWithScope(
                Class<? extends Annotation> annotationClass,
                ComponentContext injectableContext,
                A annotation,
                C context,
                List<ComponentScope> scopes) {
            Injectable injectable = getInjectable(
                    annotationClass,
                    injectableContext,
                    annotation,
                    context,
                    ComponentScope.Undefined);
            if (injectable == null) {
                return null;
            }
            return new InjectableScopePair(injectable, ComponentScope.Undefined);
        }
    }

    private static final class ConstantInjectable<T> implements Injectable<T> {
        private final T value;

        private ConstantInjectable(T value) {
            this.value = value;
        }

        @Override
        public T getValue() {
            return value;
        }
    }
}
