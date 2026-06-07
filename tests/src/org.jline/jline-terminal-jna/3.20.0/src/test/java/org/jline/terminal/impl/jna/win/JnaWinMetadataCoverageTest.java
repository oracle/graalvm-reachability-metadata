/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jline.terminal.impl.jna.win;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JnaWinMetadataCoverageTest {

    private static final InvocationHandler NO_OP_HANDLER = JnaWinMetadataCoverageTest::defaultValue;

    @Test
    void windowsProxyAndStructureMetadataRemainsUsable() {
        assertThat(JnaWinSysTerminal.class).isNotNull();
        Object proxy = Proxy.newProxyInstance(
                Kernel32.class.getClassLoader(),
                new Class<?>[]{Kernel32.class},
                NO_OP_HANDLER
        );
        assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();

        assertDeclaredFields(Kernel32.CHAR_INFO.class);
        assertDeclaredFields(Kernel32.CONSOLE_CURSOR_INFO.class);
        assertDeclaredFields(Kernel32.CONSOLE_SCREEN_BUFFER_INFO.class);
        assertDeclaredFields(Kernel32.COORD.class);
        assertDeclaredFields(Kernel32.FOCUS_EVENT_RECORD.class);
        assertDeclaredFields(Kernel32.INPUT_RECORD.class);
        assertDeclaredFields(Kernel32.INPUT_RECORD.EventUnion.class);
        assertDeclaredFields(Kernel32.KEY_EVENT_RECORD.class);
        assertDeclaredFields(Kernel32.MENU_EVENT_RECORD.class);
        assertDeclaredFields(Kernel32.MOUSE_EVENT_RECORD.class);
        assertDeclaredFields(Kernel32.SMALL_RECT.class);
        assertDeclaredFields(Kernel32.UnionChar.class);
        assertDeclaredFields(Kernel32.WINDOW_BUFFER_SIZE_RECORD.class);
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
