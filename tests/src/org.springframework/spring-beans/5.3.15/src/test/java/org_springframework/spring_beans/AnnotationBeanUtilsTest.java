/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.annotation.AnnotationBeanUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBeanUtilsTest {
    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void copiesAnnotationAttributesToBeanProperties() {
        Options options = ConfiguredType.class.getAnnotation(Options.class);
        OptionsBean bean = new OptionsBean();

        AnnotationBeanUtils.copyPropertiesToBean(options, bean);

        assertThat(bean.getName()).isEqualTo("configured");
        assertThat(bean.getCount()).isEqualTo(3);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Options {
        String name();

        int count();
    }

    @Options(name = "configured", count = 3)
    public static class ConfiguredType {
    }

    public static class OptionsBean {
        private String name;
        private int count;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
