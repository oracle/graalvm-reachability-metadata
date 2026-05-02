/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.beanutils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.configuration2.beanutils.BeanCreationContext;
import org.apache.commons.configuration2.beanutils.BeanDeclaration;
import org.apache.commons.configuration2.beanutils.ConstructorArg;
import org.apache.commons.configuration2.beanutils.DefaultBeanFactory;
import org.junit.jupiter.api.Test;

public class DefaultBeanFactoryTest {
    @Test
    void createsBeanWithMatchingPublicConstructorAndInitializesIt() throws Exception {
        final SimpleBeanDeclaration declaration = new SimpleBeanDeclaration(Arrays.asList(
                ConstructorArg.forValue("service", String.class.getName()),
                ConstructorArg.forValue("42", Integer.class.getName())));
        final RecordingBeanCreationContext context = new RecordingBeanCreationContext(
                ConfigurableBean.class, declaration);

        final Object bean = new DefaultBeanFactory().createBean(context);

        assertThat(bean).isInstanceOf(ConfigurableBean.class);
        final ConfigurableBean configurableBean = (ConfigurableBean) bean;
        assertThat(configurableBean.getName()).isEqualTo("service");
        assertThat(configurableBean.getPort()).isEqualTo(42);
        assertThat(configurableBean.isInitialized()).isTrue();
    }

    public static final class ConfigurableBean {
        private final String name;
        private final Integer port;
        private boolean initialized;

        public ConfigurableBean() {
            this("default", -1);
        }

        public ConfigurableBean(String name, Integer port) {
            this.name = name;
            this.port = port;
        }

        String getName() {
            return name;
        }

        Integer getPort() {
            return port;
        }

        boolean isInitialized() {
            return initialized;
        }

        void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }
    }

    private static final class RecordingBeanCreationContext implements BeanCreationContext {
        private final Class<?> beanClass;
        private final BeanDeclaration declaration;

        private RecordingBeanCreationContext(Class<?> beanClass, BeanDeclaration declaration) {
            this.beanClass = beanClass;
            this.declaration = declaration;
        }

        @Override
        public Class<?> getBeanClass() {
            return beanClass;
        }

        @Override
        public BeanDeclaration getBeanDeclaration() {
            return declaration;
        }

        @Override
        public Object getParameter() {
            return null;
        }

        @Override
        public void initBean(Object bean, BeanDeclaration data) {
            ((ConfigurableBean) bean).setInitialized(data == declaration);
        }

        @Override
        public Object createBean(BeanDeclaration data) {
            throw new UnsupportedOperationException("Nested bean declarations are not used by this test");
        }
    }

    private static final class SimpleBeanDeclaration implements BeanDeclaration {
        private final Collection<ConstructorArg> constructorArgs;

        private SimpleBeanDeclaration(Collection<ConstructorArg> constructorArgs) {
            this.constructorArgs = constructorArgs;
        }

        @Override
        public String getBeanFactoryName() {
            return null;
        }

        @Override
        public Object getBeanFactoryParameter() {
            return null;
        }

        @Override
        public String getBeanClassName() {
            return ConfigurableBean.class.getName();
        }

        @Override
        public Map<String, Object> getBeanProperties() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Object> getNestedBeanDeclarations() {
            return Collections.emptyMap();
        }

        @Override
        public Collection<ConstructorArg> getConstructorArgs() {
            return constructorArgs;
        }
    }
}
