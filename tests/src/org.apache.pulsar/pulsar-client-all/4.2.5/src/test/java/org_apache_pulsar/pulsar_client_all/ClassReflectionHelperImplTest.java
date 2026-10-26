/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.pulsar.shade.org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

public class ClassReflectionHelperImplTest {
    @Test
    void discoversConventionLifecycleMethods() {
        ClassReflectionHelperImpl helper = new ClassReflectionHelperImpl();
        try {
            Method postConstruct = helper.findPostConstruct(LifecycleBean.class, Object.class);
            Method preDestroy = helper.findPreDestroy(LifecycleBean.class, Object.class);

            assertThat(postConstruct.getName()).isEqualTo("postConstruct");
            assertThat(preDestroy.getName()).isEqualTo("preDestroy");
        } finally {
            helper.dispose();
        }
    }

    public static class LifecycleBean {
        public void postConstruct() {
        }

        public void preDestroy() {
        }
    }
}
