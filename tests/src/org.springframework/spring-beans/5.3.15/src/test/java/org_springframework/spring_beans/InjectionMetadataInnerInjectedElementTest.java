/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InjectionMetadata;

import static org.assertj.core.api.Assertions.assertThat;

public class InjectionMetadataInnerInjectedElementTest {

    @Test
    void injectsResourceIntoFieldThroughInjectionMetadata() throws Throwable {
        Field field = FieldInjectedBean.class.getDeclaredField("message");
        InjectionMetadata metadata = InjectionMetadata.forElements(
                Collections.singleton(new ResourceInjectedElement(field, "field value")), FieldInjectedBean.class);
        FieldInjectedBean bean = new FieldInjectedBean();

        metadata.inject(bean, "fieldInjectedBean", null);

        assertThat(bean.getMessage()).isEqualTo("field value");
    }

    @Test
    void injectsResourceIntoMethodThroughInjectionMetadata() throws Throwable {
        Method method = MethodInjectedBean.class.getDeclaredMethod("setMessage", String.class);
        InjectionMetadata metadata = InjectionMetadata.forElements(
                Collections.singleton(new ResourceInjectedElement(method, "method value")), MethodInjectedBean.class);
        MethodInjectedBean bean = new MethodInjectedBean();

        metadata.inject(bean, "methodInjectedBean", null);

        assertThat(bean.getMessage()).isEqualTo("method value");
    }

    private static final class ResourceInjectedElement extends InjectionMetadata.InjectedElement {
        private final Object resource;

        private ResourceInjectedElement(Member member, Object resource) {
            super(member, null);
            this.resource = resource;
        }

        @Override
        protected Object getResourceToInject(Object target, String requestingBeanName) {
            return this.resource;
        }
    }

    private static final class FieldInjectedBean {
        private String message;

        private String getMessage() {
            return this.message;
        }
    }

    private static final class MethodInjectedBean {
        private String message;

        private void setMessage(String message) {
            this.message = message;
        }

        private String getMessage() {
            return this.message;
        }
    }
}
