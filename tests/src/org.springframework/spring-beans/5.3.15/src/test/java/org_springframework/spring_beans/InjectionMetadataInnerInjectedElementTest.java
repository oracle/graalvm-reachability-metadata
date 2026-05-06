/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InjectionMetadata;

public class InjectionMetadataInnerInjectedElementTest {

    @Test
    public void injectsResourceIntoField() throws Throwable {
        InjectionTarget target = new InjectionTarget();
        Field field = InjectionTarget.class.getDeclaredField("fieldValue");
        InjectionMetadata metadata = new InjectionMetadata(
                InjectionTarget.class, List.of(new TestInjectedElement(field, "field dependency")));

        metadata.inject(target, "injectionTarget", null);

        assertThat(target.fieldValue).isEqualTo("field dependency");
    }

    @Test
    public void injectsResourceThroughMethod() throws Throwable {
        InjectionTarget target = new InjectionTarget();
        Method method = InjectionTarget.class.getDeclaredMethod("setMethodValue", String.class);
        InjectionMetadata metadata = new InjectionMetadata(
                InjectionTarget.class, List.of(new TestInjectedElement(method, "method dependency")));

        metadata.inject(target, "injectionTarget", null);

        assertThat(target.methodValue).isEqualTo("method dependency");
    }

    private static final class TestInjectedElement extends InjectionMetadata.InjectedElement {

        private final Object resource;

        private TestInjectedElement(Field field, Object resource) {
            super(field, null);
            this.resource = resource;
        }

        private TestInjectedElement(Method method, Object resource) {
            super(method, null);
            this.resource = resource;
        }

        @Override
        protected Object getResourceToInject(Object target, String requestingBeanName) {
            return this.resource;
        }
    }

    public static class InjectionTarget {

        private String fieldValue;

        private String methodValue;

        private void setMethodValue(String methodValue) {
            this.methodValue = methodValue;
        }
    }
}
