/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2_external.aopalliance_repackaged;

import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.ConstructorInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorInterceptorTest {
    @Test
    void adjustsArgumentsBeforeConstructingAComponent() throws Throwable {
        final ComponentConstruction invocation = new ComponentConstruction(" worker ");
        final ConstructorInterceptor normalize = construction -> {
            assertThat(construction.getThis()).isNull();
            assertThat(construction.getConstructor().getDeclaringClass()).isEqualTo(Component.class);
            assertThat(construction.getStaticPart()).isSameAs(construction.getConstructor());
            construction.getArguments()[0] = ((String) construction.getArguments()[0]).trim();
            return construction.proceed();
        };

        final Component component = (Component) normalize.construct(invocation);

        assertThat(component.name).isEqualTo("worker");
        assertThat(invocation.constructions).isEqualTo(1);
    }

    @Test
    void canReturnAnExistingComponentInsteadOfConstructingAnother() throws Throwable {
        final Component existing = new Component("shared");
        final ConstructorInterceptor singleton = construction -> existing;
        final ComponentConstruction invocation = new ComponentConstruction("unused");

        assertThat(singleton.construct(invocation)).isSameAs(existing);
        assertThat(invocation.constructions).isZero();
    }

    public static final class Component {
        private final String name;

        public Component(String name) {
            this.name = name;
        }
    }

    private static final class ComponentConstruction implements ConstructorInvocation {
        private final Object[] arguments;
        private final Constructor<Component> constructor;
        private int constructions;

        private ComponentConstruction(String name) throws NoSuchMethodException {
            this.arguments = new Object[] {name};
            this.constructor = Component.class.getConstructor(String.class);
        }

        @Override
        public Object proceed() {
            constructions++;
            return new Component((String) arguments[0]);
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public Constructor<Component> getConstructor() {
            return constructor;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return constructor;
        }
    }
}
