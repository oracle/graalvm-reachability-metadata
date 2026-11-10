/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.lang.reflect.Method;

import org.apache.ibatis.javassist.util.proxy.MethodHandler;
import org.apache.ibatis.javassist.util.proxy.Proxy;
import org.apache.ibatis.javassist.util.proxy.ProxyFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ProxyFactoryTest {
    @Test
    void readsTheActiveHandlerAfterReplacingItOnACompiledProxy() throws Throwable {
        GreetingProxy proxy = new GreetingProxy();
        MethodHandler friendly = (self, method, proceed, args) -> {
            assertSame(proxy, self);
            assertEquals("greet", method.getName());
            return "Hello, " + args[0];
        };
        MethodHandler formal = (self, method, proceed, args) -> "Welcome, " + args[0];

        proxy.setHandler(friendly);
        assertSame(friendly, ProxyFactory.getHandler(proxy));
        assertEquals("Hello, Ada", proxy.greet("Ada"));

        GreetingProxy other = new GreetingProxy();
        other.setHandler(friendly);
        proxy.setHandler(formal);
        assertSame(formal, ProxyFactory.getHandler(proxy));
        assertSame(friendly, ProxyFactory.getHandler(other));
        assertEquals("Welcome, Grace", proxy.greet("Grace"));
    }

    public interface Greeting {
        String greet(String name) throws Throwable;
    }

    public static class GreetingProxy implements Greeting, Proxy {
        private MethodHandler handler;

        @Override
        public void setHandler(MethodHandler methodHandler) {
            handler = methodHandler;
        }

        @Override
        public String greet(String name) throws Throwable {
            Method method = Greeting.class.getMethod("greet", String.class);
            return (String) handler.invoke(this, method, null, new Object[]{name});
        }
    }
}
