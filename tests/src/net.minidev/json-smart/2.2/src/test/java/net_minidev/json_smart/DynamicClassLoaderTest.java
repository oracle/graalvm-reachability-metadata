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
    void generatesBeanAccessorForPublicBeanApi() {
        try {
            BeansAccess<MutableBean> access = BeansAccess.get(MutableBean.class);
            MutableBean bean = access.newInstance();

            access.set(bean, "name", "generated-access");
            access.set(bean, "count", Integer.valueOf(7));

            assertThat(access.get(bean, "name")).isEqualTo("generated-access");
            assertThat(access.get(bean, "count")).isEqualTo(7);
            assertThat(bean.getName()).isEqualTo("generated-access");
            assertThat(bean.getCount()).isEqualTo(7);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static final class MutableBean {
        private String name;
        private int count;

        public MutableBean() {
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
