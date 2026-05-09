/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentInjector;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProviderContext;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ComponentInjectorTest {
    @Test
    void injectsAnnotatedFieldsAndSetterMethods() {
        final RecordingInjectableProviderContext context = new RecordingInjectableProviderContext();
        final ComponentInjector<SetterInjectedComponent> injector = new ComponentInjector<SetterInjectedComponent>(
                context,
                SetterInjectedComponent.class);
        final SetterInjectedComponent component = new SetterInjectedComponent();

        injector.inject(component);

        assertThat(component.fieldValue).isEqualTo("field-injection");
        assertThat(component.setterValue).isEqualTo("method-injection");
        assertThat(context.fieldInjectionRequested).isTrue();
        assertThat(context.methodInjectionRequested).isTrue();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, METHOD})
    public @interface InjectableValue {
    }

    public static class SetterInjectedComponent {
        @InjectableValue
        private String fieldValue;
        private String setterValue;

        @InjectableValue
        public void setSetterValue(String setterValue) {
            this.setterValue = setterValue;
        }
    }

    private static class RecordingInjectableProviderContext implements InjectableProviderContext {
        private boolean fieldInjectionRequested;
        private boolean methodInjectionRequested;

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
            if (ac != InjectableValue.class) {
                return null;
            }
            final AccessibleObject accessibleObject = ic.getAccesibleObject();
            if (accessibleObject instanceof Field) {
                fieldInjectionRequested = true;
                return new FixedInjectable("field-injection");
            }
            if (accessibleObject instanceof Method) {
                methodInjectionRequested = true;
                return new FixedInjectable("method-injection");
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
