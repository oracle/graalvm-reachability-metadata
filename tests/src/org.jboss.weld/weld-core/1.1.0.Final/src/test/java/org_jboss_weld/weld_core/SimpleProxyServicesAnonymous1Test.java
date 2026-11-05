/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.weld.bean.proxy.util.SimpleProxyServices;
import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SimpleProxyServicesAnonymous1Test {
    @Test
    void loadsBeanClassForProxyServices() throws Exception {
        SimpleProxyServices services = new SimpleProxyServices();
        Class<?> beanClass = services.loadBeanClass("org_jboss_weld.weld_core.ReflectionFixture");
        ReflectionFixture bean = (ReflectionFixture) beanClass.cast(
                SecureReflections.newInstance(beanClass));
        assertThat(bean.greet("proxy service")).isEqualTo("Hello proxy service");
        assertThat(services.getClassLoader(beanClass)).isSameAs(ReflectionFixture.class.getClassLoader());
    }
}
