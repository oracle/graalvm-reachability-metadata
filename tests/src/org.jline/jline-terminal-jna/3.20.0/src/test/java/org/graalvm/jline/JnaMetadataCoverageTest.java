/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.jline.terminal.impl.jna.freebsd.FreeBsdNativePty;
import org.jline.terminal.impl.jna.linux.LinuxNativePty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JnaMetadataCoverageTest {

    private static final InvocationHandler NO_OP_HANDLER = JnaMetadataCoverageTest::defaultValue;

    @Test
    void posixProxyAndStructureMetadataRemainsUsable() {
        assertProxy(org.jline.terminal.impl.jna.linux.CLibrary.class);
        assertProxy(LinuxNativePty.UtilLibrary.class);
        assertProxy(org.jline.terminal.impl.jna.freebsd.CLibrary.class);
        assertProxy(FreeBsdNativePty.UtilLibrary.class);
        assertProxy(org.jline.terminal.impl.jna.osx.CLibrary.class);
        assertProxy(org.jline.terminal.impl.jna.solaris.CLibrary.class);

        assertDeclaredFields(org.jline.terminal.impl.jna.linux.CLibrary.termios.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.linux.CLibrary.winsize.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.freebsd.CLibrary.termios.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.freebsd.CLibrary.winsize.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.osx.CLibrary.termios.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.osx.CLibrary.winsize.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.solaris.CLibrary.termios.class);
        assertDeclaredFields(org.jline.terminal.impl.jna.solaris.CLibrary.winsize.class);
    }

    private static void assertProxy(Class<?> proxyInterface) {
        Object proxy = Proxy.newProxyInstance(
                proxyInterface.getClassLoader(),
                new Class<?>[]{proxyInterface},
                NO_OP_HANDLER
        );
        assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
    }

    private static void assertDeclaredFields(Class<?> type) {
        assertThat(type.getDeclaredFields()).isNotEmpty();
    }

    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }
        return null;
    }
}
