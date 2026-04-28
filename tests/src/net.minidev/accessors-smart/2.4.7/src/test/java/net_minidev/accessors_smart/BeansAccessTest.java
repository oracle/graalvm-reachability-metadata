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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeansAccessTest {
    @Test
    void loadsPreGeneratedAccessorClassesAndSupportsNameBasedReadsWritesAndConstruction() {
        BeansAccess<PreGeneratedBean> firstAccess = BeansAccess.get(PreGeneratedBean.class);
        BeansAccess<PreGeneratedBean> secondAccess = BeansAccess.get(PreGeneratedBean.class);

        assertThat(firstAccess.getClass()).isSameAs(PreGeneratedBeanAccAccess.class);
        assertThat(secondAccess).isSameAs(firstAccess);

        PreGeneratedBean bean = firstAccess.newInstance();
        firstAccess.set(bean, "message", "ready");

        assertThat(bean.getMessage()).isEqualTo("ready");
        assertThat(firstAccess.get(bean, "message")).isEqualTo("ready");
        assertThat(firstAccess.getIndex("message")).isZero();
        assertThatThrownBy(() -> firstAccess.set(bean, "missing", "value"))
                .isInstanceOf(java.lang.NoSuchFieldException.class)
                .hasMessageContaining("failed to map field:missing");
    }

    public static final class PreGeneratedBean {
        private String message;

        public String getMessage() {
            return this.message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static final class PreGeneratedBeanAccAccess extends BeansAccess<PreGeneratedBean> {
        @Override
        public void set(PreGeneratedBean bean, int index, Object value) {
            if (index == 0) {
                bean.setMessage((String) value);
                return;
            }
            throw new IllegalArgumentException("Unknown field index: " + index);
        }

        @Override
        public Object get(PreGeneratedBean bean, int index) {
            if (index == 0) {
                return bean.getMessage();
            }
            throw new IllegalArgumentException("Unknown field index: " + index);
        }

        @Override
        public PreGeneratedBean newInstance() {
            return new PreGeneratedBean();
        }
    }
}
