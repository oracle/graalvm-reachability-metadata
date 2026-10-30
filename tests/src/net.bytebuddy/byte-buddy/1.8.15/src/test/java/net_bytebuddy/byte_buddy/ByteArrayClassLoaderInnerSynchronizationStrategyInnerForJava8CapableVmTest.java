/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.util.Map;

import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava8CapableVmTest {
    @Test
    void resolvesExistingParentTypeDuringSuccessiveInjectionRequests() throws Exception {
        ByteArrayClassLoader loader = new ByteArrayClassLoader(getClass().getClassLoader(), false, Map.of());
        byte[] bytes = ClassFileLocator.ForClassLoader.read(JavaType.class).resolve();
        Map<String, Class<?>> first = loader.defineClasses(Map.of(JavaType.class.getName(), bytes));
        assertEquals(Map.of(JavaType.class.getName(), JavaType.class), first);
        assertSame(JavaType.class, loader.defineClass(JavaType.class.getName(), bytes));
        assertSame(getClass().getClassLoader(), first.get(JavaType.class.getName()).getClassLoader());
    }
}
