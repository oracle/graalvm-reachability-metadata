/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.jboss.weld.util.reflection.instantiation.UnsafeInstantiator;
import org.junit.jupiter.api.Test;

public class UnsafeInstantiatorTest {
    @Test
    void allocatesInstanceWithoutRunningConstructor() {
        UnsafeInstantiator instantiator = new UnsafeInstantiator();
        ConstructorBean bean = instantiator.instantiate(ConstructorBean.class);
        assertThat(bean.value).isZero();
        assertThat(new ConstructorBean().value).isEqualTo(42);
    }

    public static class ConstructorBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private int value;

        public ConstructorBean() {
            value = 42;
        }
    }
}
