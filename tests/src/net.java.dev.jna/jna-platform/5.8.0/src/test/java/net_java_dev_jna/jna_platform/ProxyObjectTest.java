/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.COM.util.ProxyObject;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyObjectTest {

    @Test
    void invokeDelegatesObjectEqualsToProxyObject() throws Throwable {
        ProxyObject proxyObject = (ProxyObject) unsafe().allocateInstance(ProxyObject.class);

        Object equalsResult = proxyObject.invoke(null, Object.class.getMethod("equals", Object.class), new Object[]{null});

        assertThat(equalsResult).isEqualTo(false);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
