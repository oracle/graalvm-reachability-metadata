/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.asm.BeansAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DynamicClassLoaderTest {
    @Test
    void generatesAndUsesRuntimeBeanAccessor() {
        BeansAccess<GeneratedBean> access;
        try {
            access = BeansAccess.get(GeneratedBean.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        GeneratedBean bean = access.newInstance();
        access.set(bean, "name", "json-smart");
        access.set(bean, "count", Integer.valueOf(2));

        assertThat(access.getClass().getName()).endsWith("GeneratedBeanAccAccess");
        assertThat(access.get(bean, "name")).isEqualTo("json-smart");
        assertThat(access.get(bean, "count")).isEqualTo(Integer.valueOf(2));
        assertThat(bean.getName()).isEqualTo("json-smart");
        assertThat(bean.getCount()).isEqualTo(2);
    }

    public static class GeneratedBean {
        private String name;
        private int count;

        public GeneratedBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
