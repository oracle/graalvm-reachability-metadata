/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindMethod;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanTest {
    @Test
    void createsAndPopulatesNewJavaBean() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("pool.size", "7")));
        Pool pool = binder.bind("pool", Bindable.of(Pool.class).withBindMethod(BindMethod.JAVA_BEAN)).get();

        assertThat(pool.getSize()).isEqualTo(7);
    }

    public static class Pool {
        private int size;

        public int getSize() {
            return this.size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
