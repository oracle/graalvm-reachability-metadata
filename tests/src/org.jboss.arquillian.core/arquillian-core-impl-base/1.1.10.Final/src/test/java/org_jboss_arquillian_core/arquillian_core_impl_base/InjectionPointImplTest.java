/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.impl.InjectionPointImpl;
import org.jboss.arquillian.core.spi.InjectionPoint;
import org.junit.jupiter.api.Test;

public class InjectionPointImplTest {
    @Test
    void setInjectsInstanceIntoTargetField() throws Exception {
        InjectionTarget target = new InjectionTarget();
        Field instanceField = InjectionTarget.class.getDeclaredField("instance");
        Instance<String> instance = new StringInstance("value");

        InjectionPoint injectionPoint = InjectionPointImpl.of(target, instanceField);
        injectionPoint.set(instance);

        assertThat(target.instance()).isSameAs(instance);
        assertThat(target.instance().get()).isEqualTo("value");
        assertThat(injectionPoint.getType()).isEqualTo(String.class);
        assertThat(injectionPoint.getScope()).isEqualTo(ApplicationScoped.class);
    }

    private static final class InjectionTarget {
        @ApplicationScoped
        private Instance<String> instance;

        private Instance<String> instance() {
            return instance;
        }
    }

    private static final class StringInstance implements Instance<String> {
        private final String value;

        private StringInstance(String value) {
            this.value = value;
        }

        @Override
        public String get() {
            return value;
        }
    }
}
