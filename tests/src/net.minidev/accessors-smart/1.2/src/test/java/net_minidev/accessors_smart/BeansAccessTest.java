/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import net.minidev.asm.Accessor;
import net.minidev.asm.BeansAccess;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeansAccessTest {
    @Test
    void loadsPrecompiledAccessorClassBeforeGeneratingOne() {
        try {
            BeansAccess<SampleBean> access = BeansAccess.get(SampleBean.class);
            SampleBean bean = access.newInstance();

            assertThat(access).isInstanceOf(SampleBeanAccAccess.class);
            access.set(bean, "name", "Ada");
            access.set(bean, "age", 42);

            assertThat(access.get(bean, "name")).isEqualTo("Ada");
            assertThat(access.get(bean, "age")).isEqualTo(42);
            assertThat(access.getMap()).containsKeys("name", "age");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class SampleBean {
        private String name;
        private int age;

        public SampleBean() {
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return this.age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class SampleBeanAccAccess extends BeansAccess<SampleBean> {
        public SampleBeanAccAccess() {
        }

        @Override
        public void set(SampleBean object, int methodIndex, Object value) {
            String accessorName = accessorName(methodIndex);
            if ("name".equals(accessorName)) {
                object.setName((String) value);
            } else if ("age".equals(accessorName)) {
                object.setAge(((Number) value).intValue());
            } else {
                throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
            }
        }

        @Override
        public Object get(SampleBean object, int methodIndex) {
            String accessorName = accessorName(methodIndex);
            if ("name".equals(accessorName)) {
                return object.getName();
            } else if ("age".equals(accessorName)) {
                return object.getAge();
            }
            throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
        }

        @Override
        public SampleBean newInstance() {
            return new SampleBean();
        }

        private String accessorName(int methodIndex) {
            Accessor[] accessors = getAccessors();
            if (methodIndex < 0 || methodIndex >= accessors.length) {
                throw new IllegalArgumentException("Invalid accessor index: " + methodIndex);
            }
            return accessors[methodIndex].getName();
        }
    }
}
