/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.reflect.ReflectUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilsTest {
    @Test
    void findProxyConstructorReturnsInvocationHandlerConstructorForProxyInterface() throws NoSuchMethodException {
        Constructor<?> proxyConstructor = ReflectUtils.findProxyConstructor(ProxyContract.class.getClassLoader(), ProxyContract.class);

        assertThat(proxyConstructor.getParameterTypes()).containsExactly(InvocationHandler.class);
        assertThat(proxyConstructor.getDeclaringClass().getInterfaces()).contains(ProxyContract.class);
    }

    @Test
    void findInPublicScopeReturnsPublicParentMethodForPackagePrivateOverride() throws NoSuchMethodException {
        Method overridingMethod = PackagePrivateChild.class.getDeclaredMethod("message");

        Method publicMethod = ReflectUtils.findInPublicScope(overridingMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isSameAs(PublicParent.class);
        assertThat(publicMethod.getName()).isEqualTo("message");
    }

    @Test
    void findInPublicScopeReturnsPublicInterfaceMethodWhenNoPublicParentDeclaresIt() throws NoSuchMethodException {
        Method implementationMethod = PackagePrivateInterfaceImpl.class.getDeclaredMethod("describe");

        Method publicMethod = ReflectUtils.findInPublicScope(implementationMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isSameAs(Describable.class);
        assertThat(publicMethod.getName()).isEqualTo("describe");
    }

    public interface ProxyContract {
        String message();
    }

    public static class PublicParent {
        public String message() {
            return "parent";
        }
    }

    static class PackagePrivateChild extends PublicParent {
        @Override
        public String message() {
            return "child";
        }
    }

    public interface Describable {
        String describe();
    }

    static class PackagePrivateInterfaceImpl implements Describable {
        @Override
        public String describe() {
            return "implementation";
        }
    }
}
