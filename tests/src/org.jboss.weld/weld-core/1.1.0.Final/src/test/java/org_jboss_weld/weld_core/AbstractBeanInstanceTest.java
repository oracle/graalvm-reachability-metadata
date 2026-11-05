/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.weld.bean.proxy.TargetBeanInstance;
import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class AbstractBeanInstanceTest {
    @Test
    void dispatchesMethodToUnderlyingBean() throws Throwable {
        ReflectionFixture bean = new ReflectionFixture();
        TargetBeanInstance instance = new TargetBeanInstance(bean);
        Method greeting = SecureReflections.getMethod(ReflectionFixture.class, "greet", String.class);
        Object result = instance.invoke(instance.getInstance(), greeting, "Weld");
        assertThat(result).isEqualTo("Hello Weld");
        assertThat(bean.label).isEqualTo("Hello Weld");
        assertThat(instance.getInstanceType()).isEqualTo(ReflectionFixture.class);
    }
}
