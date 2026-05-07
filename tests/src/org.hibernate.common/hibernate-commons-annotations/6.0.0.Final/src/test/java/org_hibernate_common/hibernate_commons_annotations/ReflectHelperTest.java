/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.hibernate.annotations.common.reflection.Filter;
import org.hibernate.annotations.common.reflection.ReflectionUtil;
import org.junit.jupiter.api.Test;

public class ReflectHelperTest {
    private static final Filter DEFAULT_FILTER = new Filter() {
        @Override
        public boolean returnStatic() {
            return false;
        }

        @Override
        public boolean returnTransient() {
            return false;
        }
    };

    private static final Filter INCLUDE_TRANSIENT_FILTER = new Filter() {
        @Override
        public boolean returnStatic() {
            return false;
        }

        @Override
        public boolean returnTransient() {
            return true;
        }
    };

    @Test
    public void nonStaticGetterWithResolvedReturnTypeIsProperty() throws Exception {
        Method method = SampleBean.class.getDeclaredMethod("getName");

        assertThat(ReflectionUtil.isProperty(method, method.getGenericReturnType(), DEFAULT_FILTER)).isTrue();
    }

    @Test
    public void transientFieldIsPropertyOnlyWhenFilterIncludesTransientMembers() throws Exception {
        Field field = SampleBean.class.getDeclaredField("transientName");

        assertThat(ReflectionUtil.isProperty(field, field.getGenericType(), DEFAULT_FILTER)).isFalse();
        assertThat(ReflectionUtil.isProperty(field, field.getGenericType(), INCLUDE_TRANSIENT_FILTER)).isTrue();
    }

    public static class SampleBean {
        public transient String transientName;

        public String getName() {
            return "sample";
        }
    }
}
