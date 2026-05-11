/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_easymock.easymock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.easymock.internal.JavaProxyFactory;
import org.junit.jupiter.api.Test;

public class JavaProxyFactoryTest {
    @Test
    void createsJdkProxyForInterface() {
        JavaProxyFactory<MessageService> proxyFactory = new JavaProxyFactory<MessageService>();
        InvocationHandler handler = new MessageServiceHandler();

        MessageService proxy = proxyFactory.createProxy(MessageService.class, handler);

        assertThat(proxy.messageFor("GraalVM")).isEqualTo("Hello, GraalVM");
        assertThat(proxy.deliveryAttempts()).isEqualTo(3);
        assertThat(proxy.toString()).contains("MessageService proxy");
    }

    public interface MessageService {
        String messageFor(String recipient);

        int deliveryAttempts();
    }

    private static final class MessageServiceHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("messageFor".equals(methodName)) {
                return "Hello, " + args[0];
            }
            if ("deliveryAttempts".equals(methodName)) {
                return Integer.valueOf(3);
            }
            if ("toString".equals(methodName)) {
                return "MessageService proxy";
            }
            throw new UnsupportedOperationException("Unexpected method: " + methodName);
        }
    }
}
