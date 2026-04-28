/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import net.minidev.asm.BeansAccess;
import net.minidev.asm.ex.NoSuchFieldException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeansAccessTest {
    @Test
    void cachesGeneratedAccessorsAndSupportsNameBasedReadsWritesAndConstruction() {
        BeansAccess<MutableBean> firstAccess = BeansAccess.get(MutableBean.class);
        BeansAccess<MutableBean> secondAccess = BeansAccess.get(MutableBean.class);

        assertThat(secondAccess).isSameAs(firstAccess);

        MutableBean bean = firstAccess.newInstance();
        firstAccess.set(bean, "message", "ready");

        assertThat(bean.getMessage()).isEqualTo("ready");
        assertThat(firstAccess.get(bean, "message")).isEqualTo("ready");
        assertThat(firstAccess.getIndex("message")).isGreaterThanOrEqualTo(0);
        assertThatThrownBy(() -> firstAccess.set(bean, "missing", "value"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    public static final class MutableBean {
        private String message;

        public String getMessage() {
            return this.message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
