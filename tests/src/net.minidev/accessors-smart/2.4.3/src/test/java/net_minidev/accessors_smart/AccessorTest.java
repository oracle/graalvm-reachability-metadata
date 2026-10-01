/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import net.minidev.asm.Accessor;
import net.minidev.asm.BasicFiledFilter;

import org.junit.jupiter.api.Test;

public class AccessorTest {
    @Test
    public void discoversDeclaredSetterAndGetterMethods() throws Exception {
        Field field = AccessorBean.class.getDeclaredField("name");

        Accessor accessor = new Accessor(AccessorBean.class, field, BasicFiledFilter.SINGLETON);

        assertThat(accessor.getName()).isEqualTo("name");
        assertThat(accessor.getType()).isEqualTo(String.class);
        assertThat(accessor.getGenericType()).isEqualTo(String.class);
        assertThat(accessor.isReadable()).isTrue();
        assertThat(accessor.isWritable()).isTrue();
        assertThat(accessor.isPublic()).isFalse();
    }

    @Test
    public void fallsBackToGetGetterForBooleanFieldsWithoutIsGetter() throws Exception {
        Field field = AccessorBean.class.getDeclaredField("ready");

        Accessor accessor = new Accessor(AccessorBean.class, field, BasicFiledFilter.SINGLETON);

        assertThat(accessor.getName()).isEqualTo("ready");
        assertThat(accessor.getType()).isEqualTo(boolean.class);
        assertThat(accessor.isReadable()).isTrue();
        assertThat(accessor.isWritable()).isTrue();
        assertThat(accessor.isPublic()).isFalse();
    }

    public static class AccessorBean {
        private String name;
        private boolean ready;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean getReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }
    }
}
