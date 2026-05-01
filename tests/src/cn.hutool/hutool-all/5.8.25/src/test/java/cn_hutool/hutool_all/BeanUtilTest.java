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
    public void detectsWritableBeanFromPublicSetter() {
        assertThat(BeanUtil.hasSetter(BeanSubject.class)).isTrue();
        assertThat(BeanUtil.isBean(BeanSubject.class)).isTrue();
    }

    @Test
    public void detectsReadableBeanFromPublicGetter() {
        assertThat(BeanUtil.hasGetter(BeanSubject.class)).isTrue();
        assertThat(BeanUtil.isReadableBean(BeanSubject.class)).isTrue();
    }

    @Test
    public void detectsBeanFromPublicInstanceField() {
        assertThat(BeanUtil.hasPublicField(PublicFieldSubject.class)).isTrue();
        assertThat(BeanUtil.isBean(PublicFieldSubject.class)).isTrue();
        assertThat(BeanUtil.isReadableBean(PublicFieldSubject.class)).isTrue();
    }

    public static class BeanSubject {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class PublicFieldSubject {
        public String label = "visible";
    }
}
