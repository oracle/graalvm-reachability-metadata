/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.Serializable;

import javassist.util.proxy.ProxyFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ProxyFactoryTest {
    @Test
    void buildsDistinctCacheKeysForMethodSignaturesAndSerializationModes() {
        ProxyFactory factory = new ProxyFactory();
        Class<?>[] interfaces = {Runnable.class, Serializable.class};
        byte[] signature = {(byte) 0x12, (byte) 0xa0};
        String prefix = TestSupport.Greeting.class.getName() + ":java.lang.Runnable:java.io.Serializable:";
        String key = factory.getKey(TestSupport.Greeting.class, interfaces, signature, false);
        assertEquals(prefix + "210a", key);
        assertEquals(key + ":w", factory.getKey(TestSupport.Greeting.class, interfaces, signature, true));
        assertNotEquals(key, factory.getKey(TestSupport.Greeting.class, interfaces, new byte[]{0x13, (byte) 0xa0}, false));
        assertNotEquals(key, factory.getKey(TestSupport.Greeting.class, new Class<?>[]{Runnable.class}, signature, false));
    }
}
