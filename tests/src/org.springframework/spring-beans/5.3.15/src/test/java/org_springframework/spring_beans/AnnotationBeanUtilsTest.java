/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.annotation.AnnotationBeanUtils;

@SuppressWarnings("deprecation")
public class AnnotationBeanUtilsTest {

    @Test
    public void copyPropertiesToBeanCopiesAnnotationAttributesThroughBeanAccessors() {
        SampleAnnotation annotation = AnnotatedComponent.class.getAnnotation(SampleAnnotation.class);
        AnnotationBackedBean bean = new AnnotationBackedBean();

        AnnotationBeanUtils.copyPropertiesToBean(annotation, bean, value -> "resolved-" + value, "ignored");

        assertThat(bean.getName()).isEqualTo("resolved-spring");
        assertThat(bean.getCount()).isEqualTo(15);
        assertThat(bean.isEnabled()).isTrue();
        assertThat(bean.getIgnored()).isNull();
    }

    @SampleAnnotation(name = "spring", count = 15, enabled = true, ignored = "excluded")
    public static class AnnotatedComponent {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleAnnotation {
        String name();

        int count();

        boolean enabled();

        String ignored();
    }

    public static class AnnotationBackedBean {
        private String name;
        private int count;
        private boolean enabled;
        private String ignored;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIgnored() {
            return ignored;
        }

        public void setIgnored(String ignored) {
            this.ignored = ignored;
        }
    }
}
