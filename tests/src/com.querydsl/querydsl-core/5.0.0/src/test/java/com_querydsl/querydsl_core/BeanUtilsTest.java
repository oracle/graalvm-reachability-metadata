/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.util.BeanUtils;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class BeanUtilsTest {

    @Test
    void isAccessorPresentFindsJavaBeanAccessors() {
        assertThat(BeanUtils.isAccessorPresent("get", "name", AccessorBean.class)).isTrue();
        assertThat(BeanUtils.isAccessorPresent("is", "active", AccessorBean.class)).isTrue();
        assertThat(BeanUtils.isAccessorPresent("get", "missing", AccessorBean.class)).isFalse();
    }

    @Test
    void getAccessorReturnsMatchingJavaBeanMethod() {
        Method nameAccessor = BeanUtils.getAccessor("get", "name", AccessorBean.class);
        Method activeAccessor = BeanUtils.getAccessor("is", "active", AccessorBean.class);

        assertThat(nameAccessor).isNotNull();
        assertThat(nameAccessor.getName()).isEqualTo("getName");
        assertThat(nameAccessor.getReturnType()).isEqualTo(String.class);
        assertThat(activeAccessor).isNotNull();
        assertThat(activeAccessor.getName()).isEqualTo("isActive");
        assertThat(activeAccessor.getReturnType()).isEqualTo(boolean.class);
        assertThat(BeanUtils.getAccessor("get", "missing", AccessorBean.class)).isNull();
    }

    public static class AccessorBean {

        public AccessorBean() {
        }

        public String getName() {
            return "querydsl";
        }

        public boolean isActive() {
            return true;
        }
    }
}
