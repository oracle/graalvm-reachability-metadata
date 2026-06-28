/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import com.ibm.websphere.management.AdminServiceFactory;
import org.junit.jupiter.api.Test;

import org.springframework.jmx.support.WebSphereMBeanServerFactoryBean;

public class WebSphereMBeanServerFactoryBeanTest {

    @Test
    void resolvesMBeanServerThroughWebSphereAdminServiceFactoryApi() {
        final WebSphereMBeanServerFactoryBean factoryBean = new WebSphereMBeanServerFactoryBean();
        final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
        assertThat(AdminServiceFactory.getMBeanFactory().getMBeanServer()).isSameAs(platformMBeanServer);

        factoryBean.afterPropertiesSet();
        assertThat(factoryBean.getObject()).isSameAs(platformMBeanServer);
        assertThat(factoryBean.getObjectType()).isSameAs(platformMBeanServer.getClass());
        assertThat(factoryBean.isSingleton()).isTrue();
    }
}
