/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import net.minidev.asm.Accessor;
import net.minidev.asm.BeansAccess;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessorTest {
    @Test
    void discoversSetterAndBooleanGetter() {
        try {
            BeansAccess<BooleanGetterBean> access = BeansAccess.get(BooleanGetterBean.class);
            BooleanGetterBean bean = access.newInstance();

            access.set(bean, "enabled", true);

            assertThat(bean.isEnabled()).isTrue();
            assertThat(access.get(bean, "enabled")).isEqualTo(Boolean.TRUE);
            Accessor accessor = access.getMap().get("enabled");
            assertThat(accessor).isNotNull();
            assertThat(accessor.getName()).isEqualTo("enabled");
            assertThat(accessor.getType()).isEqualTo(boolean.class);
            assertThat(accessor.isReadable()).isTrue();
            assertThat(accessor.isWritable()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void discoversSetterAndBooleanGetterFallback() {
        try {
            BeansAccess<BooleanGetterFallbackBean> access = BeansAccess.get(BooleanGetterFallbackBean.class);
            BooleanGetterFallbackBean bean = access.newInstance();

            access.set(bean, "active", true);

            assertThat(bean.getActive()).isTrue();
            assertThat(access.get(bean, "active")).isEqualTo(Boolean.TRUE);
            Accessor accessor = access.getMap().get("active");
            assertThat(accessor).isNotNull();
            assertThat(accessor.getName()).isEqualTo("active");
            assertThat(accessor.getType()).isEqualTo(boolean.class);
            assertThat(accessor.isReadable()).isTrue();
            assertThat(accessor.isWritable()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class BooleanGetterBean {
        private boolean enabled;

        public BooleanGetterBean() {
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class BooleanGetterFallbackBean {
        private boolean active;

        public BooleanGetterFallbackBean() {
        }

        public boolean getActive() {
            return this.active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
