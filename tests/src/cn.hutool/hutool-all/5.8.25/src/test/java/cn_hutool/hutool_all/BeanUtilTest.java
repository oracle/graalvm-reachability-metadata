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
    void recognizesBeanAccessorsAndPublicFields() {
        assertThat(BeanUtil.hasSetter(AccessorBean.class)).isTrue();
        assertThat(BeanUtil.hasGetter(AccessorBean.class)).isTrue();
        assertThat(BeanUtil.hasPublicField(PublicFieldBean.class)).isTrue();

        assertThat(BeanUtil.isBean(AccessorBean.class)).isTrue();
        assertThat(BeanUtil.isReadableBean(PublicFieldBean.class)).isTrue();
    }

    @Test
    void rejectsClassesWithoutBeanAccessorsOrPublicInstanceFields() {
        assertThat(BeanUtil.hasSetter(NonBean.class)).isFalse();
        assertThat(BeanUtil.hasGetter(NonBean.class)).isFalse();
        assertThat(BeanUtil.hasPublicField(NonBean.class)).isFalse();

        assertThat(BeanUtil.isBean(NonBean.class)).isFalse();
        assertThat(BeanUtil.isReadableBean(NonBean.class)).isFalse();
    }

    public static class AccessorBean {
        private String name;
        private boolean active;

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class PublicFieldBean {
        public String title = "public value";
    }

    public static class NonBean {
        public static String shared = "static value";
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }
}
