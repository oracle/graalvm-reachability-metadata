/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.Decorator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DecoratedObjectFactoryCoverageTest {
    public static class DecoratedValue {
        private boolean decorated;

        public boolean isDecorated() {
            return decorated;
        }

        public void setDecorated(boolean decorated) {
            this.decorated = decorated;
        }
    }

    @Test
    void decoratedObjectFactoryCreatesInstancesReflectively() throws Exception {
        DecoratedObjectFactory factory = new DecoratedObjectFactory();
        factory.addDecorator(new Decorator() {
            @Override
            public <T> T decorate(T object) {
                if (object instanceof DecoratedValue) {
                    ((DecoratedValue) object).setDecorated(true);
                }
                return object;
            }

            @Override
            public void destroy(Object object) {
            }
        });

        DecoratedValue value = factory.createInstance(DecoratedValue.class);
        assertThat(value.isDecorated()).isTrue();
    }
}
