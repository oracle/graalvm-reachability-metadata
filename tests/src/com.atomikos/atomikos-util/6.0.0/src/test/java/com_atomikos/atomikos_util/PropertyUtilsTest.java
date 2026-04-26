/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import com.atomikos.beans.PropertyException;
import com.atomikos.beans.PropertyUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyUtilsTest {

    @Test
    void setPropertyAutoInstantiatesNestedBeansAndConvertsSupportedTypes() throws PropertyException {
        RootBean rootBean = new RootBean();

        PropertyUtils.setProperty(rootBean, "nested.count", "37");
        PropertyUtils.setProperty(rootBean, "nested.tags", "alpha,beta");

        assertThat(rootBean.getNested()).isNotNull();
        assertThat(rootBean.getNested().getCount()).isEqualTo(37);
        assertThat(rootBean.getNested().getTags()).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void setPropertyAllowsNullAssignmentsForDirectProperties() throws PropertyException {
        NullableBean nullableBean = new NullableBean();
        nullableBean.setValue("before");

        PropertyUtils.setProperty(nullableBean, "value", null);

        assertThat(nullableBean.getValue()).isNull();
    }

    @Test
    void containsSetterForGetterRecognizesBooleanBeanAccessors() throws Exception {
        Method getter = BooleanBean.class.getMethod("isReady");

        assertThat(invokeContainsSetterForGetter(BooleanBean.class, getter)).isTrue();
    }

    private static boolean invokeContainsSetterForGetter(Class<?> targetType, Method getter) throws Exception {
        Method containsSetterForGetter = PropertyUtils.class.getDeclaredMethod(
            "containsSetterForGetter",
            Class.class,
            Method.class
        );
        containsSetterForGetter.setAccessible(true);
        return (Boolean) containsSetterForGetter.invoke(null, targetType, getter);
    }

    public static final class RootBean {
        private NestedBean nested;

        public NestedBean getNested() {
            return this.nested;
        }

        public void setNested(NestedBean nested) {
            this.nested = nested;
        }
    }

    public static final class NestedBean {
        private int count;
        private Set<String> tags;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public Set<String> getTags() {
            return this.tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }
    }

    public static final class NullableBean {
        private String value;

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static final class BooleanBean {
        private boolean ready;

        public boolean isReady() {
            return this.ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }
    }
}
