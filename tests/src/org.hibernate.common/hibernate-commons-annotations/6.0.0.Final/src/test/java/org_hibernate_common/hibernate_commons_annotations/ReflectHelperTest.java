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
    private static final Filter INSTANCE_PROPERTY_FILTER = new Filter() {
        @Override
        public boolean returnStatic() {
            return false;
        }

        @Override
        public boolean returnTransient() {
            return false;
        }
    };

    @Test
    public void isPropertyRecognizesInstanceGetterMethods() throws Exception {
        Method getter = SampleBean.class.getDeclaredMethod("getName");
        Method operation = SampleBean.class.getDeclaredMethod("calculateName", String.class);

        assertThat(ReflectionUtil.isProperty(getter, getter.getGenericReturnType(), INSTANCE_PROPERTY_FILTER)).isTrue();
        assertThat(ReflectionUtil.isProperty(operation, operation.getGenericReturnType(), INSTANCE_PROPERTY_FILTER))
                .isFalse();
    }

    @Test
    public void isPropertyHonorsFieldStaticAndTransientFilters() throws Exception {
        Field instanceField = SampleBean.class.getDeclaredField("name");
        Field staticField = SampleBean.class.getDeclaredField("sharedName");
        Field transientField = SampleBean.class.getDeclaredField("temporaryName");

        assertThat(ReflectionUtil.isProperty(instanceField, instanceField.getGenericType(), INSTANCE_PROPERTY_FILTER))
                .isTrue();
        assertThat(ReflectionUtil.isProperty(staticField, staticField.getGenericType(), INSTANCE_PROPERTY_FILTER))
                .isFalse();
        assertThat(ReflectionUtil.isProperty(transientField, transientField.getGenericType(), INSTANCE_PROPERTY_FILTER))
                .isFalse();
    }

    public static class SampleBean {
        private static String sharedName = "shared";

        private String name = "hibernate";

        private transient String temporaryName = "temporary";

        public String getName() {
            return name;
        }

        public String calculateName(String suffix) {
            return name + suffix;
        }
    }
}
