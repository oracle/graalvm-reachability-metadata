/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.util.BeanUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanUtilsTest {
    @Test
    void isAccessorPresentFindsJavaBeanGettersAndBooleanAccessors() {
        assertThat(BeanUtils.isAccessorPresent("get", "name", AccessorFixture.class)).isTrue();
        assertThat(BeanUtils.isAccessorPresent("is", "active", AccessorFixture.class)).isTrue();
        assertThat(BeanUtils.isAccessorPresent("get", "missing", AccessorFixture.class)).isFalse();
    }

    @Test
    void getAccessorReturnsPublicAccessorMethodOrNull() {
        Method nameAccessor = BeanUtils.getAccessor("get", "name", AccessorFixture.class);
        Method activeAccessor = BeanUtils.getAccessor("is", "active", AccessorFixture.class);

        assertThat(nameAccessor).isNotNull();
        assertThat(nameAccessor.getName()).isEqualTo("getName");
        assertThat(nameAccessor.getReturnType()).isEqualTo(String.class);
        assertThat(activeAccessor).isNotNull();
        assertThat(activeAccessor.getName()).isEqualTo("isActive");
        assertThat(activeAccessor.getReturnType()).isEqualTo(boolean.class);
        assertThat(BeanUtils.getAccessor("get", "missing", AccessorFixture.class)).isNull();
    }

    public static final class AccessorFixture {
        public String getName() {
            return "querydsl";
        }

        public boolean isActive() {
            return true;
        }
    }
}
