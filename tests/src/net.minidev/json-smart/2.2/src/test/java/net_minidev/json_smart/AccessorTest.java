/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.asm.BeansAccess;
import org.junit.jupiter.api.Test;

public class AccessorTest {
    @Test
    void discoversBooleanBeanSetterAndGetterMethods() {
        BeansAccess<BooleanGetterBean> access = BeansAccess.get(BooleanGetterBean.class);
        BooleanGetterBean bean = access.newInstance();

        access.set(bean, "enabled", Boolean.TRUE);

        assertThat(bean.getEnabled()).isTrue();
        assertThat(access.get(bean, "enabled")).isEqualTo(Boolean.TRUE);
    }

    public static class BooleanGetterBean {
        private boolean enabled;

        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class BooleanGetterBeanAccAccess extends BeansAccess<BooleanGetterBean> {
        @Override
        public void set(BooleanGetterBean object, int methodIndex, Object value) {
            String name = getAccessors()[methodIndex].getName();
            if ("enabled".equals(name)) {
                object.setEnabled(((Boolean) value).booleanValue());
                return;
            }
            throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
        }

        @Override
        public Object get(BooleanGetterBean object, int methodIndex) {
            String name = getAccessors()[methodIndex].getName();
            if ("enabled".equals(name)) {
                return Boolean.valueOf(object.getEnabled());
            }
            throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
        }

        @Override
        public BooleanGetterBean newInstance() {
            return new BooleanGetterBean();
        }
    }
}
