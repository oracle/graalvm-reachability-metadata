/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;

import org.apache.commons.configuration.beanutils.BeanDeclaration;
import org.apache.commons.configuration.beanutils.DefaultBeanFactory;
import org.junit.jupiter.api.Test;

public class DefaultBeanFactoryTest {
    @Test
    public void createBeanInstantiatesPublicClassWithDefaultConstructor() throws Exception {
        DefaultBeanFactory factory = DefaultBeanFactory.INSTANCE;
        BeanDeclaration declaration = new EmptyBeanDeclaration();

        Object bean = factory.createBean(Properties.class, declaration, "ignored parameter");

        assertThat(bean).isInstanceOf(Properties.class);
        assertThat(((Properties) bean)).isEmpty();
        assertThat(factory.getDefaultBeanClass()).isNull();
    }

    private static final class EmptyBeanDeclaration implements BeanDeclaration {
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
            return null;
        }

        @Override
        public Map getBeanProperties() {
            return null;
        }

        @Override
        public Map getNestedBeanDeclarations() {
            return null;
        }
    }
}
