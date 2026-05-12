/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

public class ClassMapTest {
    @Test
    void populatesMethodCacheFromPublicTargetClass() throws Exception {
        ClassMap classMap = new ClassMap(SampleBean.class, new Log(new NullLogSystem()));

        Method greeting = classMap.findMethod("greeting", new Object[] {"Ada"});
        Method doubled = classMap.findMethod("doubled", new Object[] {Integer.valueOf(21)});

        assertThat(classMap.getCachedClass()).isSameAs(SampleBean.class);
        assertThat(greeting).isNotNull();
        assertThat(greeting.getDeclaringClass()).isSameAs(SampleBean.class);
        assertThat(greeting.getParameterTypes()).containsExactly(String.class);
        assertThat(doubled).isNotNull();
        assertThat(doubled.getDeclaringClass()).isSameAs(SampleBean.class);
        assertThat(doubled.getParameterTypes()).containsExactly(Integer.TYPE);
    }

    public static final class SampleBean {
        public String greeting(final String name) {
            return "Hello " + name;
        }

        public int doubled(final int value) {
            return value * 2;
        }
    }
}
