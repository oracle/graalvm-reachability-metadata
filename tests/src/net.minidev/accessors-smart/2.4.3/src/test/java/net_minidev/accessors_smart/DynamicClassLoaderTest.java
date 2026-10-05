/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.asm.BeansAccess;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DynamicClassLoaderTest {
    @Test
    public void definesGeneratedAccessorAndUsesItForBeanAccess() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            BeansAccess<MutableBean> access = BeansAccess.get(MutableBean.class);
            MutableBean bean = access.newInstance();

            access.set(bean, "name", "json-smart");
            access.set(bean, "count", Integer.valueOf(7));

            assertThat(access.get(bean, "name")).isEqualTo("json-smart");
            assertThat(access.get(bean, "count")).isEqualTo(Integer.valueOf(7));
            assertThat(bean.getName()).isEqualTo("json-smart");
            assertThat(bean.getCount()).isEqualTo(Integer.valueOf(7));
        });
    }

    public static class MutableBean {
        private String name;
        private Integer count;

        public MutableBean() {
        }

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
}
