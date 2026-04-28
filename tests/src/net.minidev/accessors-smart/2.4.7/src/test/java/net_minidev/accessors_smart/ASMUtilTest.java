/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import java.util.Arrays;

import net.minidev.asm.Accessor;
import net.minidev.asm.BeansAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ASMUtilTest {
    @Test
    void collectsDeclaredFieldsFromTypeHierarchy() {
        BeansAccess<DerivedBean> access = BeansAccess.get(DerivedBean.class);

        assertThat(Arrays.stream(access.getAccessors())
                .map(Accessor::getName)
                .toList())
                .containsExactlyInAnyOrder("parentValue", "childValue");

        DerivedBean bean = access.newInstance();
        access.set(bean, "parentValue", "parent");
        access.set(bean, "childValue", 7);

        assertThat(bean.getParentValue()).isEqualTo("parent");
        assertThat(bean.getChildValue()).isEqualTo(7);
    }

    public static class ParentBean {
        private String parentValue;

        public String getParentValue() {
            return this.parentValue;
        }

        public void setParentValue(String parentValue) {
            this.parentValue = parentValue;
        }
    }

    public static final class DerivedBean extends ParentBean {
        private int childValue;

        public int getChildValue() {
            return this.childValue;
        }

        public void setChildValue(int childValue) {
            this.childValue = childValue;
        }
    }
}
