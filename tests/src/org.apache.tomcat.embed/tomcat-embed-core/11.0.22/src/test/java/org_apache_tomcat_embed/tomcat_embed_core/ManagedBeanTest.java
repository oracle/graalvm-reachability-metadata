/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.management.DynamicMBean;

import org.apache.catalina.mbeans.NamingResourcesMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedBeanTest {

    @Test
    void createsConfiguredModelMBean() throws Exception {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setClassName(NamingResourcesMBean.class.getName());
        descriptor.setName("configuredBean");
        descriptor.setDescription("Configured model MBean");

        DynamicMBean mBean = descriptor.createMBean("managed-value");

        assertThat(mBean).isInstanceOf(NamingResourcesMBean.class);
        assertThat(mBean.getMBeanInfo().getClassName()).isEqualTo(NamingResourcesMBean.class.getName());
        assertThat(mBean.getMBeanInfo().getDescription()).isEqualTo("Configured model MBean");
        assertThat(mBean.getAttribute("modelerType")).isEqualTo(String.class.getName());
    }
}
