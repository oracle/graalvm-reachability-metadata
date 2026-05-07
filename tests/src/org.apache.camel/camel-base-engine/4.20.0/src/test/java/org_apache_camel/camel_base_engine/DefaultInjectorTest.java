/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base_engine;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.engine.DefaultInjector;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultInjectorTest {
    @Test
    void newInstanceInvokesPublicStaticFactoryMethodAndInjectsCamelContext() throws Exception {
        try (SimpleCamelContext camelContext = new SimpleCamelContext(false)) {
            DefaultInjector injector = new DefaultInjector(camelContext);

            FactoryCreatedBean bean = injector.newInstance(FactoryCreatedBean.class, "create");

            assertThat(bean).isNotNull();
            assertThat(bean.isCreatedByFactory()).isTrue();
            assertThat(bean.getCamelContext()).isSameAs(camelContext);
        }
    }

    public static final class FactoryCreatedBean implements CamelContextAware {
        private CamelContext camelContext;
        private boolean createdByFactory;

        public static FactoryCreatedBean create() {
            FactoryCreatedBean bean = new FactoryCreatedBean();
            bean.createdByFactory = true;
            return bean;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        boolean isCreatedByFactory() {
            return createdByFactory;
        }
    }
}
