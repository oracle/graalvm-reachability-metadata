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
    void isAccessorPresentFindsPublicBeanAccessor() {
        boolean present = BeanUtils.isAccessorPresent("get", "name", SampleBean.class);
        boolean absent = BeanUtils.isAccessorPresent("get", "missing", SampleBean.class);

        assertThat(present).isTrue();
        assertThat(absent).isFalse();
    }

    @Test
    void getAccessorReturnsPublicBeanAccessorMethod() {
        Method method = BeanUtils.getAccessor("is", "active", SampleBean.class);
        Method missingMethod = BeanUtils.getAccessor("get", "missing", SampleBean.class);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("isActive");
        assertThat(method.getReturnType()).isEqualTo(boolean.class);
        assertThat(missingMethod).isNull();
    }

    public static final class SampleBean {
        private final String name = "querydsl";
        private final boolean active = true;

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }
    }
}
