/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class JerseyReflectionHelperAnonymous15Test {
    @Test
    void obtainsRequestedConstructorThroughPrivilegedAction() {
        Constructor<?> constructor =
                ReflectionHelper.getDeclaredConstructorPA(ConstructorBean.class, String.class).run();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    public static class ConstructorBean {
        public ConstructorBean(String value) {
        }
    }
}
