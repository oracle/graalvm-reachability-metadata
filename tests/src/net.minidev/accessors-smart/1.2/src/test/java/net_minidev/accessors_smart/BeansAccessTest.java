/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.asm.BeansAccess;

import org.junit.jupiter.api.Test;

public class BeansAccessTest {
    @Test
    public void loadsExistingAccessorClassForBeanType() {
        assertThat(PredefinedBeanAccAccess.class.getName())
                .isEqualTo(PredefinedBean.class.getName() + "AccAccess");

        BeansAccess<PredefinedBean> access = BeansAccess.get(PredefinedBean.class);
        PredefinedBean bean = access.newInstance();

        access.set(bean, "name", "loaded-accessor");
        access.set(bean, "count", Integer.valueOf(42));

        assertThat(access).isInstanceOf(PredefinedBeanAccAccess.class);
        assertThat(access.get(bean, "name")).isEqualTo("loaded-accessor");
        assertThat(access.get(bean, "count")).isEqualTo(Integer.valueOf(42));
    }

    public static class PredefinedBean {
        private String name;
        private Integer count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }

    public static class PredefinedBeanAccAccess extends BeansAccess<PredefinedBean> {
        @Override
        public void set(PredefinedBean object, int methodIndex, Object value) {
            switch (getAccessors()[methodIndex].getName()) {
                case "name" -> object.setName((String) value);
                case "count" -> object.setCount((Integer) value);
                default -> throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
            }
        }

        @Override
        public Object get(PredefinedBean object, int methodIndex) {
            return switch (getAccessors()[methodIndex].getName()) {
                case "name" -> object.getName();
                case "count" -> object.getCount();
                default -> throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
            };
        }

        @Override
        public PredefinedBean newInstance() {
            return new PredefinedBean();
        }
    }
}
