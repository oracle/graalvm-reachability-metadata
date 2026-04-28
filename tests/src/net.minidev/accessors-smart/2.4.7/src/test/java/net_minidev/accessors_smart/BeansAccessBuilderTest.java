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

public class BeansAccessBuilderTest {
    @Test
    void appliesDefaultConvertersWhenWritingPrimitiveProperties() {
        BeansAccess<ConvertibleBean> access = BeansAccess.get(ConvertibleBean.class);
        ConvertibleBean bean = access.newInstance();

        access.set(bean, "count", "41");
        access.set(bean, "enabled", 1);

        assertThat(bean.getCount()).isEqualTo(41);
        assertThat(bean.isEnabled()).isTrue();
        assertThat(access.get(bean, "count")).isEqualTo(41);
        assertThat(access.get(bean, "enabled")).isEqualTo(Boolean.TRUE);
    }

    public static final class ConvertibleBean {
        private int count;
        private boolean enabled;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
