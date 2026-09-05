/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class InjectionMetadataInnerInjectedElementTest {
    @Test
    void extensionInjectsFieldsAndMethods() throws Throwable {
        InjectionTarget target = new InjectionTarget();
        Field field = ReflectionUtils.findField(InjectionTarget.class, "fieldValue");
        Method method = BeanUtils.findDeclaredMethod(InjectionTarget.class, "setMethodValue", String.class);

        new ConstantInjectedElement(field, null, "field").apply(target);
        new ConstantInjectedElement(method, null, "method").apply(target);

        assertThat(target.getFieldValue()).isEqualTo("field");
        assertThat(target.getMethodValue()).isEqualTo("method");
    }

    private static class ConstantInjectedElement extends InjectionMetadata.InjectedElement {
        private final String value;

        ConstantInjectedElement(Member member, PropertyDescriptor descriptor, String value) {
            super(member, descriptor);
            this.value = value;
        }

        void apply(Object target) throws Throwable {
            inject(target, null, null);
        }

        @Override
        protected Object getResourceToInject(Object target, String requestingBeanName) {
            return this.value;
        }
    }

    public static class InjectionTarget {
        private String fieldValue;
        private String methodValue;

        public String getFieldValue() {
            return this.fieldValue;
        }

        public String getMethodValue() {
            return this.methodValue;
        }

        private void setMethodValue(String methodValue) {
            this.methodValue = methodValue;
        }
    }
}
