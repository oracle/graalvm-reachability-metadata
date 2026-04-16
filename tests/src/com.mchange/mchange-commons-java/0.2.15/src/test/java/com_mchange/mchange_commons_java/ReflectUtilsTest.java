/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.mchange.v2.reflect.ReflectUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilsTest {
    @Test
    void findProxyConstructorReturnsInvocationHandlerConstructorForProxyInterface() throws Exception {
        Constructor<?> proxyConstructor = ReflectUtils.findProxyConstructor(
            ReflectUtilsTest.class.getClassLoader(),
            new Class<?>[] { ProxiedAction.class }
        );
        InvocationHandler invocationHandler = (proxy, method, args) -> "handled";

        ProxiedAction proxiedAction = (ProxiedAction) proxyConstructor.newInstance(invocationHandler);

        assertThat(Proxy.isProxyClass(proxyConstructor.getDeclaringClass())).isTrue();
        assertThat(proxyConstructor.getParameterTypes()).containsExactly(InvocationHandler.class);
        assertThat(proxiedAction.execute()).isEqualTo("handled");
    }

    @Test
    void findInPublicScopeResolvesMethodFromPublicParentClass() throws Exception {
        Method hiddenMethod = HiddenParentOverride.class.getDeclaredMethod("describe");

        Method publicMethod = ReflectUtils.findInPublicScope(hiddenMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(PublicParent.class);
        assertThat(publicMethod.invoke(new HiddenParentOverride())).isEqualTo("child");
    }

    @Test
    void findInPublicScopeResolvesMethodFromPublicInterface() throws Exception {
        Method hiddenMethod = HiddenInterfaceImplementation.class.getDeclaredMethod("name");

        Method publicMethod = ReflectUtils.findInPublicScope(hiddenMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(NamedContract.class);
        assertThat(publicMethod.invoke(new HiddenInterfaceImplementation())).isEqualTo("value");
    }

    public interface ProxiedAction {
        String execute();
    }

    public static class PublicParent {
        public String describe() {
            return "parent";
        }
    }

    public interface NamedContract {
        String name();
    }
}

class HiddenParentOverride extends ReflectUtilsTest.PublicParent {
    @Override
    public String describe() {
        return "child";
    }
}

class HiddenInterfaceImplementation implements ReflectUtilsTest.NamedContract {
    @Override
    public String name() {
        return "value";
    }
}
