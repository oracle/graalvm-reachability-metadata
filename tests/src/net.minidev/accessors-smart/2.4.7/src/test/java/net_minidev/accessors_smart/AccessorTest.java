/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import net.minidev.asm.Accessor;
import net.minidev.asm.BeansAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessorTest {
    @Test
    void resolvesBooleanGetterFallbackAndExposesAccessorMetadata() {
        BeansAccess<BooleanPropertyBean> access = BeansAccess.get(BooleanPropertyBean.class);
        Accessor accessor = access.getMap().get("active");

        assertThat(accessor).isNotNull();
        assertThat(accessor.getName()).isEqualTo("active");
        assertThat(accessor.getType()).isEqualTo(boolean.class);
        assertThat(accessor.isReadable()).isTrue();
        assertThat(accessor.isWritable()).isTrue();

        BooleanPropertyBean bean = access.newInstance();
        access.set(bean, "active", "true");

        assertThat(bean.getActive()).isTrue();
        assertThat(access.get(bean, "active")).isEqualTo(Boolean.TRUE);
    }

    public static final class BooleanPropertyBean {
        private boolean active;

        public boolean getActive() {
            return this.active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
