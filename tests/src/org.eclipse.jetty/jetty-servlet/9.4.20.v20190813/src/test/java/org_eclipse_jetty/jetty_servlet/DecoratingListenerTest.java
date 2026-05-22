/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jetty.servlet.DecoratingListener;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

public class DecoratingListenerTest {
    private static final String DECORATOR_ATTRIBUTE = "test.decorator";

    @Test
    public void constructorAdaptsExistingAttributeWithDecoratorMethods() {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        MethodBasedDecorator decorator = new MethodBasedDecorator();
        context.setAttribute(DECORATOR_ATTRIBUTE, decorator);

        DecoratingListener listener = new DecoratingListener(context, DECORATOR_ATTRIBUTE);
        Payload payload = new Payload();

        Payload decorated = context.getObjectFactory().decorate(payload);
        context.getObjectFactory().destroy(decorated);

        assertThat(listener.getAttributeName()).isEqualTo(DECORATOR_ATTRIBUTE);
        assertThat(decorated).isSameAs(payload);
        assertThat(payload.isDecorated()).isTrue();
        assertThat(payload.isDestroyed()).isTrue();
    }

    public static class MethodBasedDecorator {
        public Object decorate(Object object) {
            if (object instanceof Payload) {
                ((Payload) object).markDecorated();
            }
            return object;
        }

        public void destroy(Object object) {
            if (object instanceof Payload) {
                ((Payload) object).markDestroyed();
            }
        }
    }

    public static class Payload {
        private boolean decorated;
        private boolean destroyed;

        public void markDecorated() {
            decorated = true;
        }

        public void markDestroyed() {
            destroyed = true;
        }

        public boolean isDecorated() {
            return decorated;
        }

        public boolean isDestroyed() {
            return destroyed;
        }
    }
}
