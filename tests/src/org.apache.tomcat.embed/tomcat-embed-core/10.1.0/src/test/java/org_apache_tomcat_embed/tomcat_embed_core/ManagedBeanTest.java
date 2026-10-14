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

    private static final String CONTEXT_ONLY_MBEAN = "context.only.NamingResourcesMBean";

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

    @Test
    void createsModelMBeanResolvedByThreadContextClassLoader() throws Exception {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setClassName(CONTEXT_ONLY_MBEAN);
        descriptor.setName("contextLoadedBean");
        descriptor.setDescription("Context-loaded model MBean");
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        DynamicMBean mBean;
        try {
            currentThread.setContextClassLoader(new ContextAliasClassLoader());
            mBean = descriptor.createMBean("context-managed-value");
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }

        assertThat(mBean).isInstanceOf(NamingResourcesMBean.class);
        assertThat(mBean.getMBeanInfo().getClassName()).isEqualTo(CONTEXT_ONLY_MBEAN);
        assertThat(mBean.getMBeanInfo().getDescription()).isEqualTo("Context-loaded model MBean");
        assertThat(mBean.getAttribute("modelerType")).isEqualTo(String.class.getName());
    }

    private static final class ContextAliasClassLoader extends ClassLoader {

        private ContextAliasClassLoader() {
            super(ManagedBeanTest.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_ONLY_MBEAN.equals(name)) {
                return NamingResourcesMBean.class;
            }
            return super.loadClass(name);
        }
    }
}
