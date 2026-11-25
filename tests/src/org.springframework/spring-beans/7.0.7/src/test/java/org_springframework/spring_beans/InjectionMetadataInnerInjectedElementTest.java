/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.reflect.Member;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InjectionMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InjectionMetadataInnerInjectedElementTest {
    @Test
    void injectsResourcesIntoMembersDescribedByMetadata() throws Throwable {
        InjectionMetadata metadata = new InjectionMetadata(TargetBean.class, List.of(
                new ResourceElement(TargetBean.class.getDeclaredField("fieldValue"), "field"),
                new ResourceElement(TargetBean.class.getDeclaredMethod("configure", String.class), "method")));
        TargetBean target = new TargetBean();
        metadata.inject(target, "target", null);
        assertEquals("field", target.fieldValue);
        assertEquals("method", target.methodValue);
    }

    public static class ResourceElement extends InjectionMetadata.InjectedElement {
        private final String value;

        public ResourceElement(Member member, String value) {
            super(member, null);
            this.value = value;
        }

        @Override
        protected Object getResourceToInject(Object target, String requestingBeanName) {
            return this.value;
        }
    }

    public static class TargetBean {
        private String fieldValue;
        private String methodValue;

        private void configure(String value) {
            this.methodValue = value;
        }
    }
}
