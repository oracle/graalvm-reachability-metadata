/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import net.minidev.asm.BeansAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicClassLoaderTest {
    @Test
    void generatesAccessorClassesWhenNoPrebuiltAccessorExists() {
        BeansAccess<RuntimeGeneratedBean> access = BeansAccess.get(RuntimeGeneratedBean.class);
        RuntimeGeneratedBean bean = access.newInstance();

        access.set(bean, "count", "7");
        access.set(bean, "label", "generated");

        assertThat(access.getClass().getName()).isEqualTo(RuntimeGeneratedBean.class.getName() + "AccAccess");
        assertThat(bean.getCount()).isEqualTo(7);
        assertThat(bean.getLabel()).isEqualTo("generated");
        assertThat(access.get(bean, "count")).isEqualTo(7);
        assertThat(access.get(bean, "label")).isEqualTo("generated");
    }

    public static final class RuntimeGeneratedBean {
        private int count;
        private String label;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getLabel() {
            return this.label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
