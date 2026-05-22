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
import com.sun.jersey.spi.inject.InjectableProviderContext.InjectableScopePair;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentInjectorTest {
    @Test
    public void injectsAnnotatedFieldsAndSetterMethods() {
        final InjectableProviderContext injectableProviderContext = new AnnotationValueInjectableProviderContext();
        final ComponentInjector<InjectableComponent> componentInjector = new ComponentInjector<>(
                injectableProviderContext,
                InjectableComponent.class);
        final InjectableComponent component = new InjectableComponent();

        componentInjector.inject(component);

        assertThat(component.getFieldValue()).isEqualTo("field-value");
        assertThat(component.getMethodValue()).isEqualTo("method-value");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface TestInjection {
        String value();
    }

    public static class InjectableComponent {
        @TestInjection("field-value")
        private String fieldValue;

        private String methodValue;

        @TestInjection("method-value")
        public void setMethodValue(String methodValue) {
            this.methodValue = methodValue;
        }

        public String getFieldValue() {
            return fieldValue;
        }

        public String getMethodValue() {
            return methodValue;
        }
    }

    private static final class AnnotationValueInjectableProviderContext implements InjectableProviderContext {
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
                final TestInjection testInjection = (TestInjection) annotation;
                return new ConstantInjectable(testInjection.value());
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
                final TestInjection testInjection = (TestInjection) annotation;
                return new ConstantInjectable(testInjection.value());
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
