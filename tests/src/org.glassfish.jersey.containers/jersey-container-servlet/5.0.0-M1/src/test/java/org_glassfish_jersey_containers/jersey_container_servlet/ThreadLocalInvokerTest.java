/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_containers.jersey_container_servlet;

import java.lang.reflect.Proxy;

import org.glassfish.jersey.servlet.internal.ThreadLocalInvoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalInvokerTest {
    @Test
    void proxyMethodInvocationDelegatesToThreadLocalInstance() {
        ThreadLocalInvoker<CharSequence> invoker = new ThreadLocalInvoker<>();
        invoker.set("Jersey servlet");
        CharSequence proxy = (CharSequence) Proxy.newProxyInstance(
                ThreadLocalInvokerTest.class.getClassLoader(),
                new Class<?>[] {CharSequence.class},
                invoker);

        int length = proxy.length();
        char firstCharacter = proxy.charAt(0);
        CharSequence prefix = proxy.subSequence(0, 6);

        assertThat(length).isEqualTo("Jersey servlet".length());
        assertThat(firstCharacter).isEqualTo('J');
        assertThat(prefix).hasToString("Jersey");
    }
}
