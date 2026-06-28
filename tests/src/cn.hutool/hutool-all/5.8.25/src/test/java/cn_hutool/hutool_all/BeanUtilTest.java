/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.bean.BeanUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanUtilTest {
    @Test
    void detectsWritableReadableAndPublicFieldBeanShapes() {
        assertThat(BeanUtil.hasSetter(AccessorBean.class)).isTrue();
        assertThat(BeanUtil.hasGetter(AccessorBean.class)).isTrue();
        assertThat(BeanUtil.hasPublicField(PublicFieldBean.class)).isTrue();

        assertThat(BeanUtil.isBean(AccessorBean.class)).isTrue();
        assertThat(BeanUtil.isReadableBean(PublicFieldBean.class)).isTrue();
    }

    public static class AccessorBean {
        private String name;
        private boolean active;

        public AccessorBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static class PublicFieldBean {
        public String value;

        public PublicFieldBean() {
        }
    }
}
