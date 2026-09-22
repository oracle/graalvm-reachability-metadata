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

    private static final String CONTEXT_LOADER_NAME = "context.loader.CustomModelMBean";

    @Test
    void createsCustomModelMBeanResolvedByContextClassLoader() throws Exception {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setClassName(CONTEXT_LOADER_NAME);
        descriptor.setName("contextLoadedBean");
        descriptor.setDescription("Model MBean resolved from the application context");

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ModelMBeanClassLoader contextClassLoader = new ModelMBeanClassLoader(originalClassLoader);
        DynamicMBean mBean;
        try {
            currentThread.setContextClassLoader(contextClassLoader);
            mBean = descriptor.createMBean("managed-value");
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }

        assertThat(contextClassLoader.wasRequested()).isTrue();
        assertThat(mBean).isInstanceOf(NamingResourcesMBean.class);
        assertThat(mBean.getMBeanInfo().getClassName()).isEqualTo(CONTEXT_LOADER_NAME);
        assertThat(mBean.getMBeanInfo().getDescription())
                .isEqualTo("Model MBean resolved from the application context");
        assertThat(mBean.getAttribute("modelerType")).isEqualTo(String.class.getName());
    }

    private static final class ModelMBeanClassLoader extends ClassLoader {

        private boolean requested;

        private ModelMBeanClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_LOADER_NAME.equals(name)) {
                requested = true;
                return NamingResourcesMBean.class;
            }
            return super.loadClass(name);
        }

        private boolean wasRequested() {
            return requested;
        }
    }
}
