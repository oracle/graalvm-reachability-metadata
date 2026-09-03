/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanUtilsTest {
    @SuppressWarnings("deprecation")
    @Test
    void exercisesInstantiationMethodDiscoveryEditorsAndPropertyCopying() {
        assertThat(BeanUtils.instantiate(DefaultBean.class)).isInstanceOf(DefaultBean.class);
        assertThat(BeanUtils.instantiateClass(DefaultBean.class).getValue()).isEqualTo("default");

        Constructor<SinglePublicConstructorBean> publicConstructor =
                BeanUtils.getResolvableConstructor(SinglePublicConstructorBean.class);
        assertThat(BeanUtils.instantiateClass(publicConstructor, "created").getValue()).isEqualTo("created");
        assertThat(BeanUtils.getResolvableConstructor(SingleHiddenConstructorBean.class).getParameterCount())
                .isEqualTo(1);
        assertThat(BeanUtils.getResolvableConstructor(MultipleConstructorBean.class).getParameterCount())
                .isZero();

        assertThat(BeanUtils.findMethod(MethodBean.class, "visible", String.class)).isNotNull();
        assertThat(BeanUtils.findDeclaredMethod(MethodBean.class, "hidden")).isNotNull();
        assertThat(BeanUtils.findMethodWithMinimalParameters(MethodBean.class, "visible").getParameterCount())
                .isZero();
        assertThat(BeanUtils.findDeclaredMethodWithMinimalParameters(MethodBean.class, "hidden"))
                .isNotNull();

        SourceBean source = new SourceBean();
        source.setName("spring");
        TargetBean target = new TargetBean();
        BeanUtils.copyProperties(source, target);
        assertThat(target.getName()).isEqualTo("spring");
    }

    public static class DefaultBean {
        private final String value;

        public DefaultBean() {
            this.value = "default";
        }

        public String getValue() {
            return this.value;
        }
    }

    public static class SinglePublicConstructorBean {
        private final String value;

        public SinglePublicConstructorBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

    private static class SingleHiddenConstructorBean {
        private SingleHiddenConstructorBean(String value) {
        }
    }

    public static class MultipleConstructorBean {
        public MultipleConstructorBean() {
        }

        public MultipleConstructorBean(String value) {
        }
    }

    public static class MethodBean {
        public void visible() {
        }

        public void visible(String value) {
        }

        private void hidden() {
        }
    }

    public static class SourceBean {
        private String name;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TargetBean {
        private String name;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
